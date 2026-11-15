package com.example.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AdBlockPreset(
    val id: String,
    val title: String,
    val description: String,
    val url: String
)

data class AdBlockSyncResult(
    val success: Boolean,
    val rulesLoaded: Int = 0,
    val message: String
)

/**
 * Sincronizador de listas públicas fortes (Hosts / Adblock / Domains) em streaming linha-a-linha:
 * Suporta formatos:
 * - hosts (`0.0.0.0 domain.com`, `127.0.0.1 domain.com`)
 * - Adblock (`||domain.com^`)
 * - Wildcard OISD (`*.domain.com`)
 * - Lista simples (`domain.com`)
 */
object AdBlockListSync {

    val presets = listOf(
        AdBlockPreset(
            id = "hagezi_pro",
            title = "HaGeZi Multi PRO",
            description = "Filtro muito forte para APKs, banners, vídeo e trackers sem quebrar apps.",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/hosts/pro.txt"
        ),
        AdBlockPreset(
            id = "oisd_big",
            title = "OISD Big Unified",
            description = "Lista massiva anti-anúncios para sites, streaming e Android APKs.",
            url = "https://big.oisd.nl/domainswild"
        ),
        AdBlockPreset(
            id = "stevenblack",
            title = "StevenBlack Hosts",
            description = "Lista unificada clássica de Adware + Malware em formato hosts.",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        ),
        AdBlockPreset(
            id = "adguard_dns",
            title = "AdGuard DNS Filter",
            description = "Filtro oficial comunitário de publicidade web e mobile.",
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
        )
    )

    suspend fun syncFromUrl(rawUrl: String, maxRules: Int = 250_000): AdBlockSyncResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            return@withContext AdBlockSyncResult(
                success = false,
                message = "URL inválida. Indique um link http:// ou https:// de lista hosts/adblock."
            )
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ADM-MURIBA-VPN/4.0")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext AdBlockSyncResult(
                    success = false,
                    message = "Servidor devolveu HTTP $code ao descarregar lista AdBlock."
                )
            }

            val parsed = HashSet<String>(16_384)
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8), 65_536).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null && parsed.size < maxRules) {
                    parseFilterLine(line!!)?.let { domain ->
                        parsed.add(domain)
                    }
                }
            }

            if (parsed.isEmpty()) {
                return@withContext AdBlockSyncResult(
                    success = false,
                    message = "Nenhum domínio válido encontrado na lista fornecida."
                )
            }

            val label = presets.firstOrNull { it.url == url }?.title
                ?: url.substringAfter("://").substringBefore('/')
            AdBlockEngine.replaceExternalRules(parsed, label)
            AdBlockSyncResult(
                success = true,
                rulesLoaded = parsed.size,
                message = "Atualizado ($label): ${parsed.size} domínios de anúncios carregados offline."
            )
        } catch (e: Exception) {
            AdBlockSyncResult(
                success = false,
                message = "Falha ao sincronizar lista AdBlock (${e.message ?: "sem ligação"})."
            )
        } finally {
            conn?.disconnect()
        }
    }

    fun parseFilterLine(rawLine: String): String? {
        var s = rawLine.trim()
        if (s.isEmpty()) return null
        if (s[0] == '#' || s[0] == '!' || s[0] == '[' || s[0] == '@' || s[0] == '/') return null

        val hashIdx = s.indexOf('#')
        if (hashIdx > 0) s = s.substring(0, hashIdx).trim()

        if (s.startsWith("||")) {
            s = s.substring(2)
            val endCaret = s.indexOf('^')
            val endSlash = s.indexOf('/')
            val endDollar = s.indexOf('$')
            val cutAt = listOf(endCaret, endSlash, endDollar).filter { it >= 0 }.minOrNull()
            if (cutAt != null) s = s.substring(0, cutAt)
            return AdBlockEngine.normalizeDomain(s)
        }

        if (s.startsWith("*.")) {
            return AdBlockEngine.normalizeDomain(s.substring(2))
        }

        if (s.startsWith("0.0.0.0") || s.startsWith("127.0.0.1") || s.startsWith("::1") || s.startsWith("::")) {
            val parts = s.split(Regex("\\s+"))
            if (parts.size >= 2) {
                return AdBlockEngine.normalizeDomain(parts[1])
            }
            return null
        }

        if (!s.contains(' ') && !s.contains('/') && !s.contains('*')) {
            return AdBlockEngine.normalizeDomain(s)
        }
        return null
    }
}
