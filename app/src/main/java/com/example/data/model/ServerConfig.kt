package com.example.data.model

enum class ServerSourceType(val id: String, val label: String) {
    LOCAL("LOCAL", "Privado Local"),
    CLOUD_PRIVATE("CLOUD_PRIVATE", "Cloud Privado"),
    CLOUD_CATALOG("CLOUD_CATALOG", "Catálogo Cloud");

    companion object {
        fun fromId(id: String?): ServerSourceType {
            return entries.firstOrNull { it.id == id } ?: LOCAL
        }
    }
}

data class ServerConfig(
    val id: Long = 0L,
    val name: String = "",
    val sshHost: String = "",
    val sshPort: Int = 22,
    val username: String = "",
    val password: String = "",
    val tlsEnabled: Boolean = false,
    val sniHost: String = "",
    val tlsPort: Int = 443,
    val customPayload: String = "",
    val isSelected: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: ServerSourceType = ServerSourceType.LOCAL,
    val region: String = "",
    val catalogUrl: String = "",
    val lastLatencyMs: Long? = null,
    val lastSpeedBps: Long = 0L,
    val lastScore: Int = 0,
    val isFavorite: Boolean = false
) {
    val effectivePort: Int
        get() = sshPort

    val displayTitle: String
        get() = name.ifBlank { sshHost.ifBlank { "Servidor sem nome" } }

    val summaryProtocol: String
        get() = buildString {
            append(if (tlsEnabled) "SSH + TLS" else "SSH Direto")
            if (tlsEnabled && sniHost.isNotBlank()) append(" • SNI")
            if (customPayload.isNotBlank()) append(" • Payload")
            if (region.isNotBlank()) append(" • $region")
        }
}
