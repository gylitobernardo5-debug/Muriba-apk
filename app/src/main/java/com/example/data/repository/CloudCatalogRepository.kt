package com.example.data.repository

import com.example.data.model.ServerConfig
import com.example.data.model.ServerSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class CloudServerJsonItem(
    val name: String = "",
    val host: String = "",
    val sshHost: String = "",
    val port: Int = 22,
    val sshPort: Int = 0,
    val username: String = "",
    val password: String = "",
    val tls: Boolean = false,
    val tlsEnabled: Boolean = false,
    val sni: String = "",
    val sniHost: String = "",
    val payload: String = "",
    val customPayload: String = "",
    val region: String = "",
    val sourceType: String = "CLOUD_CATALOG"
)

@Serializable
data class CloudCatalogEnvelope(
    val servers: List<CloudServerJsonItem> = emptyList()
)

data class CloudSyncResult(
    val success: Boolean,
    val importedCount: Int = 0,
    val fromOfflineCache: Boolean = false,
    val message: String = ""
)

/**
 * Sistema Cloud Server Infinity:
 * - Importa e sincroniza catálogos JSON remotos (suporta milhares de entradas com persistência paginada em Room)
 * - Suporta também colagem direta de JSON no dispositivo
 * - Mantém funcionamento completo offline em Room caso o servidor remoto esteja indisponível
 * - Nunca fabrica servidores fictícios
 */
class CloudCatalogRepository(
    private val serverRepository: ServerRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun syncFromRemoteUrl(catalogUrl: String): CloudSyncResult = withContext(Dispatchers.IO) {
        val cleanedUrl = catalogUrl.trim()
        if (!cleanedUrl.startsWith("https://", ignoreCase = true) &&
            !cleanedUrl.startsWith("http://", ignoreCase = true)
        ) {
            return@withContext CloudSyncResult(
                success = false,
                message = "URL inválida. Utilize http:// ou https:// apontando para um catálogo JSON real."
            )
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(cleanedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ADM-MURIBA-VPN/4.0")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val cachedCount = serverRepository.countFilteredServers("ALL", "")
                return@withContext CloudSyncResult(
                    success = false,
                    fromOfflineCache = cachedCount > 0,
                    message = "Servidor remoto devolveu HTTP $code. Cache offline Room mantida ($cachedCount servidores)."
                )
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val imported = importJsonText(body, sourceUrl = cleanedUrl)
            if (imported <= 0) {
                return@withContext CloudSyncResult(
                    success = false,
                    message = "O JSON recebido não contém nenhum servidor SSH válido."
                )
            }
            CloudSyncResult(
                success = true,
                importedCount = imported,
                message = "Sincronizados $imported servidor(es) do catálogo remoto para Room offline."
            )
        } catch (e: Exception) {
            val cachedCount = serverRepository.countFilteredServers("ALL", "")
            CloudSyncResult(
                success = false,
                fromOfflineCache = cachedCount > 0,
                message = "Falha de rede ao contactar catálogo (${e.message ?: "offline"}). Cache local ativa ($cachedCount)."
            )
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun importJsonText(rawJson: String, sourceUrl: String = ""): Int = withContext(Dispatchers.IO) {
        val parsedItems = parseCatalogPayload(rawJson)
        val validConfigs = parsedItems.mapNotNull { item ->
            val host = item.sshHost.ifBlank { item.host }.trim()
            val port = (if (item.sshPort > 0) item.sshPort else item.port).coerceIn(1, 65535)
            val user = item.username.trim()
            if (host.isEmpty() || user.isEmpty()) return@mapNotNull null

            ServerConfig(
                name = item.name.trim().ifBlank { host },
                sshHost = host,
                sshPort = port,
                username = user,
                password = item.password,
                tlsEnabled = item.tlsEnabled || item.tls,
                sniHost = item.sniHost.ifBlank { item.sni }.trim(),
                tlsPort = port,
                customPayload = item.customPayload.ifBlank { item.payload },
                sourceType = ServerSourceType.fromId(item.sourceType),
                region = item.region.trim(),
                catalogUrl = sourceUrl
            )
        }
        if (validConfigs.isEmpty()) return@withContext 0

        // Batch chunking evita pressão de memória ao importar milhares de servidores
        var total = 0
        validConfigs.chunked(250).forEach { chunk ->
            total += serverRepository.upsertCloudServers(chunk)
        }
        total
    }

    fun parseCatalogPayload(rawJson: String): List<CloudServerJsonItem> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            json.decodeFromString<List<CloudServerJsonItem>>(trimmed)
        } else {
            json.decodeFromString<CloudCatalogEnvelope>(trimmed).servers
        }
    }
}
