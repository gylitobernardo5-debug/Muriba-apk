package com.example.vpn

/**
 * Cache DNS em memória RAM (LRU puro em Kotlin) com respeito ao TTL, eliminando latência
 * de abertura de canais SSH para resoluções repetidas e mantendo o ID de transação original.
 */
class DnsCache(private val maxEntries: Int = 256) {

    private data class CachedEntry(
        val responseBytes: ByteArray,
        val expiresAtMs: Long
    )

    private val cache = object : LinkedHashMap<String, CachedEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntry>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: String, requestIdHi: Byte, requestIdLo: Byte): ByteArray? {
        val entry = synchronized(cache) { cache[key] } ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            synchronized(cache) { cache.remove(key) }
            return null
        }
        val copy = entry.responseBytes.copyOf()
        if (copy.size >= 2) {
            copy[0] = requestIdHi
            copy[1] = requestIdLo
        }
        return copy
    }

    fun put(key: String, responseBytes: ByteArray, ttlSeconds: Long = 90L) {
        if (responseBytes.size < 12) return
        // Só armazena respostas sem erro (RCODE == 0)
        val rcode = responseBytes[3].toInt() and 0x0F
        if (rcode != 0) return
        val clampedTtlMs = ttlSeconds.coerceIn(30L, 300L) * 1000L
        synchronized(cache) {
            cache[key] = CachedEntry(
                responseBytes = responseBytes.copyOf(),
                expiresAtMs = System.currentTimeMillis() + clampedTtlMs
            )
        }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }
}
