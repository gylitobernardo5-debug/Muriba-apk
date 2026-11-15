package com.example.vpn

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object PayloadProcessor {

    /**
     * Formata tags comuns de payload estilo BadNet / HTTP Injector para transmissão real no socket.
     */
    fun buildPayloadChunks(
        rawPayload: String,
        targetHost: String,
        targetPort: Int
    ): List<ByteArray> {
        if (rawPayload.isBlank()) return emptyList()

        val normalized = rawPayload
            .replace("[host_port]", "$targetHost:$targetPort")
            .replace("[host]", targetHost)
            .replace("[port]", targetPort.toString())
            .replace("[protocol]", "HTTP/1.1")
            .replace("[netData]", "CONNECT $targetHost:$targetPort HTTP/1.1")
            .replace("[realData]", "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost\r\n\r\n")
            .replace("[ua]", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .replace("[crlf]", "\r\n")
            .replace("[lf]", "\n")
            .replace("[cr]", "\r")
            .replace("\\r\\n", "\r\n")
            .replace("\\n", "\n")
            .replace("\\r", "\r")

        // Garante que payloads escritos como texto normal em múltiplas linhas usem CRLF e terminem com linha em branco
        val withCrlf = if (!normalized.contains("\r\n") && normalized.contains("\n")) {
            normalized.replace("\n", "\r\n")
        } else {
            normalized
        }

        val splitTokens = withCrlf
            .replace("[delay_split]", "[split]")
            .split("[split]")
            .mapIndexed { idx, part ->
                val text = if (idx == withCrlf.split("[split]").lastIndex &&
                    !part.endsWith("\r\n\r\n") &&
                    part.isNotBlank()
                ) {
                    if (part.endsWith("\r\n")) "$part\r\n" else "$part\r\n\r\n"
                } else {
                    part
                }
                text.toByteArray(StandardCharsets.ISO_8859_1)
            }
            .filter { it.isNotEmpty() }

        return splitTokens
    }

    fun sendPayload(
        output: OutputStream,
        rawPayload: String,
        targetHost: String,
        targetPort: Int,
        onLog: (String) -> Unit
    ) {
        val chunks = buildPayloadChunks(rawPayload, targetHost, targetPort)
        if (chunks.isEmpty()) return

        onLog("A enviar payload personalizado (${chunks.size} bloco(s))...")
        chunks.forEachIndexed { index, bytes ->
            output.write(bytes)
            output.flush()
            if (index < chunks.lastIndex) {
                Thread.sleep(150)
            }
        }
    }

    /**
     * Lê a resposta HTTP do servidor/proxy após o envio do payload, caso exista cabeçalho HTTP/.
     * Se o servidor responder diretamente com o banner SSH-2.0, devolve os bytes no fluxo.
     */
    fun consumeHttpResponseIfPresent(
        input: InputStream,
        onLog: (String) -> Unit
    ): InputStream {
        val buffered = BufferedInputStream(input, 8192)
        buffered.mark(16)
        val probe = ByteArray(5)
        val probeRead = buffered.read(probe)
        if (probeRead <= 0) {
            throw IllegalStateException("Ligação fechada pelo servidor após envio do payload.")
        }
        val prefix = String(probe, 0, probeRead, StandardCharsets.ISO_8859_1)
        buffered.reset()

        if (!prefix.startsWith("HTTP/")) {
            onLog("Servidor iniciou handshake SSH direto sem cabeçalho HTTP intermediário.")
            return buffered
        }

        // Lê até \r\n\r\n (fim dos cabeçalhos HTTP)
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (headerBytes.size() < 16384) {
            val b = buffered.read()
            if (b == -1) break
            headerBytes.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) break else if (b == '\r'.code) 1 else 0
                else -> 0
            }
        }

        val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
        val statusLine = headerText.lineSequence().firstOrNull()?.trim().orEmpty()
        onLog("Resposta do Payload: $statusLine")

        // Verifica códigos HTTP válidos para túnel (101 Switching Protocols, 200 OK/Connection established)
        val parts = statusLine.split(" ")
        val statusCode = parts.getOrNull(1)?.toIntOrNull()
        if (statusCode != null && statusCode !in listOf(101, 200)) {
            throw IllegalStateException("Payload rejeitado pelo servidor: $statusLine")
        }

        return buffered
    }
}
