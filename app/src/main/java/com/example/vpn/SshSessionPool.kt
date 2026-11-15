package com.example.vpn

import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.lang.reflect.Method
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desbloqueia os limites rígidos de 128KB/16KB no ChannelDirectTCPIP do JSch (para até 4MB / 64KB),
 * eliminando o maior gargalo matemático de RTT (~6 MB/s em ligações com 20-30ms de latência).
 */
object SshChannelOptimizer {
    private val setLwMaxMethod: Method? by lazy {
        runCatching {
            Channel::class.java.getDeclaredMethod("setLocalWindowSizeMax", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        }.getOrNull()
    }
    private val setLwSizeMethod: Method? by lazy {
        runCatching {
            Channel::class.java.getDeclaredMethod("setLocalWindowSize", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        }.getOrNull()
    }
    private val setLmpSizeMethod: Method? by lazy {
        runCatching {
            Channel::class.java.getDeclaredMethod("setLocalPacketSize", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    fun applyHighSpeedWindow(channel: ChannelDirectTCPIP, speedMode: SpeedMode) {
        val windowBytes = speedMode.sshLocalWindowBytes.coerceIn(131_072, 4 * 1024 * 1024)
        val packetBytes = speedMode.sshLocalPacketBytes.coerceIn(16_384, 65_536)
        runCatching {
            setLwMaxMethod?.invoke(channel, windowBytes)
            setLwSizeMethod?.invoke(channel, windowBytes)
            setLmpSizeMethod?.invoke(channel, packetBytes)
        }
    }
}

/**
 * Pool multi-sessão SSH+TLS (1 a 4 túneis paralelos consoante SpeedMode):
 * - Distribui fluxos TCP em round-robin para reduzir bloqueio Head-of-Line (TCP-over-TCP)
 * - Permite desencriptação SSH em múltiplos núcleos ARM64/ARMv7 em paralelo
 * - Reutiliza SSLContext para TLS 1.3 Session Resumption nos túneis adicionais
 * - Se o servidor SSH limitar logins simultâneos, opera normalmente com as sessões aceites
 */
class SshSessionPool(
    val primarySession: Session,
    private val sessions: CopyOnWriteArrayList<Session>,
    val sshHandshakeMs: Long,
    val tlsHandshakeMs: Long,
    val tlsProtocol: String,
    val tlsCipher: String
) {
    private val rrCounter = AtomicInteger(0)

    val activeCount: Int
        get() = sessions.count { it.isConnected }

    val primaryConnected: Boolean
        get() = sessions.any { it.isConnected }

    fun pickSession(): Session {
        val list = sessions
        val size = list.size
        if (size <= 1) return primarySession
        val startIdx = (rrCounter.getAndIncrement() and 0x7FFFFFFF) % size
        for (i in 0 until size) {
            val candidate = list[(startIdx + i) % size]
            if (candidate.isConnected) return candidate
        }
        return primarySession
    }

    fun openOptimizedChannel(remoteHost: String, remotePort: Int, speedMode: SpeedMode): ChannelDirectTCPIP {
        val session = pickSession()
        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(remoteHost)
        channel.setPort(remotePort)
        SshChannelOptimizer.applyHighSpeedWindow(channel, speedMode)
        return channel
    }

    fun disconnectAll() {
        sessions.forEach { session ->
            runCatching { session.disconnect() }
        }
        sessions.clear()
    }

    companion object {
        suspend fun establish(
            server: ServerConfig,
            speedMode: SpeedMode,
            keepAlive: Boolean,
            protectSocket: (Socket) -> Boolean,
            onLog: (String) -> Unit
        ): SshSessionPool = coroutineScope {
            val jsch = JSch()
            val sharedSsl = if (server.tlsEnabled) SshTlsSocketFactory.createOptimizedSslContext() else null

            val t0 = System.currentTimeMillis()
            val primaryFactory = SshTlsSocketFactory(
                config = server,
                speedMode = speedMode,
                keepAlive = keepAlive,
                sharedSslContext = sharedSsl,
                protectSocket = protectSocket,
                onLog = onLog
            )
            val primary = jsch.getSession(server.username.trim(), server.sshHost.trim(), server.effectivePort).apply {
                setPassword(server.password)
                SshTlsSocketFactory.applySpeedOptions(this, speedMode, keepAlive)
                setSocketFactory(primaryFactory)
            }
            primary.connect(15_000)
            val sshMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

            val activeSessions = CopyOnWriteArrayList<Session>().apply { add(primary) }
            val desiredCount = speedMode.parallelSshSessions.coerceIn(1, 4)

            if (desiredCount > 1) {
                val extraJobs = (2..desiredCount).map { idx ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val factory = SshTlsSocketFactory(
                                config = server,
                                speedMode = speedMode,
                                keepAlive = keepAlive,
                                sharedSslContext = sharedSsl,
                                protectSocket = protectSocket,
                                onLog = { /* evita poluir logs com sessões paralelas */ }
                            )
                            val s = jsch.getSession(server.username.trim(), server.sshHost.trim(), server.effectivePort).apply {
                                setPassword(server.password)
                                SshTlsSocketFactory.applySpeedOptions(this, speedMode, keepAlive)
                                setSocketFactory(factory)
                            }
                            s.connect(10_000)
                            if (s.isConnected) s else null
                        }.getOrNull()
                    }
                }
                val extras = extraJobs.awaitAll().filterNotNull()
                activeSessions.addAll(extras)
                if (extras.isNotEmpty()) {
                    onLog("Multi-Core Pool: ${activeSessions.size}/$desiredCount túneis SSH paralelos ativos")
                } else {
                    onLog("Servidor limita sessões paralelas; a operar com 1 túnel + janela SSH ${speedMode.sshLocalWindowBytes / 1024}KB")
                }
            }

            SshSessionPool(
                primarySession = primary,
                sessions = activeSessions,
                sshHandshakeMs = sshMs,
                tlsHandshakeMs = primaryFactory.lastTlsHandshakeMs.get(),
                tlsProtocol = primaryFactory.lastTlsProtocol,
                tlsCipher = primaryFactory.lastTlsCipher
            )
        }
    }
}
