package com.example.vpn

import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket

/**
 * Extreme TLS & Transport SocketFactory V4:
 * - Usa SSLContext partilhado com cache de sessões TLS 1.3 (Session Resumption / Tickets) para reduzir handshakes no pool multi-túnel
 * - Mantém validação estrita de certificados TLS e SNI real
 * - Envolve o InputStream em BufferedInputStream grande (até 256 KB) para cortar drasticamente chamadas JNI ao BoringSSL/Conscrypt
 * - Prioriza TLS 1.3 e cifras AES-GCM / ChaCha20-Poly1305 aceleradas nativamente em ARMv7/ARM64 sem compressão zlib
 */
class SshTlsSocketFactory(
    private val config: ServerConfig,
    private val speedMode: SpeedMode,
    private val keepAlive: Boolean,
    private val sharedSslContext: SSLContext? = createOptimizedSslContext(),
    private val protectSocket: (Socket) -> Boolean,
    private val onLog: (String) -> Unit
) : SocketFactory {

    val lastTlsHandshakeMs = AtomicLong(0L)

    @Volatile
    var lastTlsProtocol: String = ""
        private set

    @Volatile
    var lastTlsCipher: String = ""
        private set

    companion object {
        fun createOptimizedSslContext(): SSLContext? {
            return runCatching {
                SSLContext.getInstance("TLSv1.3").apply {
                    init(null, null, null)
                    clientSessionContext?.apply {
                        sessionCacheSize = 64
                        sessionTimeout = 1800
                    }
                }
            }.getOrElse {
                runCatching {
                    SSLContext.getInstance("TLS").apply {
                        init(null, null, null)
                        clientSessionContext?.apply {
                            sessionCacheSize = 64
                            sessionTimeout = 1800
                        }
                    }
                }.getOrNull()
            }
        }

        /**
         * Cifras SSH2 de alta vazão em hardware ARM64/ARMv7 sem compressão zlib.
         */
        fun buildOptimizedSessionConfig(): Properties {
            return Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "password,keyboard-interactive")
                put("compression.s2c", "none")
                put("compression.c2s", "none")
                put("Compression", "no")
                put(
                    "cipher.s2c",
                    "aes128-gcm@openssh.com,chacha20-poly1305@openssh.com,aes128-ctr,aes256-gcm@openssh.com,aes256-ctr,aes192-ctr"
                )
                put(
                    "cipher.c2s",
                    "aes128-gcm@openssh.com,chacha20-poly1305@openssh.com,aes128-ctr,aes256-gcm@openssh.com,aes256-ctr,aes192-ctr"
                )
            }
        }

        fun applySpeedOptions(session: Session, speedMode: SpeedMode, keepAlive: Boolean) {
            session.setConfig(buildOptimizedSessionConfig())
            session.timeout = 15_000
            if (keepAlive) {
                session.serverAliveInterval = speedMode.keepAliveIntervalMs
                session.serverAliveCountMax = 3
            }
        }
    }

    private var wrappedInputStream: InputStream? = null
    private var wrappedOutputStream: OutputStream? = null

    override fun createSocket(host: String, port: Int): Socket {
        val connectPort = config.effectivePort
        val rawSocket = Socket()
        protectSocket(rawSocket)

        runCatching {
            rawSocket.receiveBufferSize = speedMode.socketBufferBytes
            rawSocket.sendBufferSize = speedMode.socketBufferBytes
        }
        rawSocket.tcpNoDelay = speedMode.tcpNoDelay
        rawSocket.keepAlive = keepAlive
        rawSocket.soTimeout = 15_000

        onLog("TCP (${speedMode.label}) -> $host:$connectPort [buf=${speedMode.socketBufferBytes / 1024}KB]")
        rawSocket.connect(InetSocketAddress(host, connectPort), 12_000)

        val activeSocket: Socket = if (config.tlsEnabled) {
            val sniTarget = config.sniHost.trim().ifBlank { host }
            onLog("Handshake TLS 1.3/1.2 (SNI: $sniTarget)...")
            val sslFactory = sharedSslContext?.socketFactory
                ?: (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory)
            val sslSocket = sslFactory.createSocket(
                rawSocket,
                sniTarget,
                connectPort,
                true
            ) as SSLSocket

            val supportedProtocols = sslSocket.supportedProtocols.toSet()
            val preferredProtocols = listOf("TLSv1.3", "TLSv1.2").filter { it in supportedProtocols }
            if (preferredProtocols.isNotEmpty()) {
                sslSocket.enabledProtocols = preferredProtocols.toTypedArray()
            }

            val params: SSLParameters = sslSocket.sslParameters
            if (config.sniHost.isNotBlank()) {
                params.serverNames = listOf(SNIHostName(config.sniHost.trim()))
            }
            sslSocket.sslParameters = params
            sslSocket.enableSessionCreation = true
            sslSocket.soTimeout = 15_000

            val t0 = System.currentTimeMillis()
            sslSocket.startHandshake()
            val tlsMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            lastTlsHandshakeMs.set(tlsMs)

            val sslSession = sslSocket.session
            lastTlsProtocol = sslSession.protocol.orEmpty()
            lastTlsCipher = sslSession.cipherSuite.orEmpty()
            onLog("TLS ativo (${lastTlsProtocol} • ${lastTlsCipher} • ${tlsMs}ms)")
            sslSocket
        } else {
            lastTlsHandshakeMs.set(0L)
            lastTlsProtocol = ""
            lastTlsCipher = ""
            rawSocket
        }

        val ioBufBytes = speedMode.socketBufferBytes.coerceIn(64 * 1024, 256 * 1024)
        val rawIn: InputStream = if (config.customPayload.isNotBlank()) {
            PayloadProcessor.sendPayload(
                output = activeSocket.getOutputStream(),
                rawPayload = config.customPayload,
                targetHost = config.sshHost.trim(),
                targetPort = config.sshPort,
                onLog = onLog
            )
            PayloadProcessor.consumeHttpResponseIfPresent(
                input = activeSocket.getInputStream(),
                onLog = onLog
            )
        } else {
            activeSocket.getInputStream()
        }

        // BufferedInputStream reduz drasticamente chamadas nativas JNI SSL_read()/recv() por pacote SSH
        wrappedInputStream = if (rawIn is BufferedInputStream) rawIn else BufferedInputStream(rawIn, ioBufBytes)
        wrappedOutputStream = BufferedOutputStream(activeSocket.getOutputStream(), 65_536)

        activeSocket.soTimeout = 0
        return activeSocket
    }

    override fun getInputStream(socket: Socket): InputStream {
        return wrappedInputStream ?: BufferedInputStream(socket.getInputStream(), 131_072)
    }

    override fun getOutputStream(socket: Socket): OutputStream {
        return wrappedOutputStream ?: BufferedOutputStream(socket.getOutputStream(), 65_536)
    }
}
