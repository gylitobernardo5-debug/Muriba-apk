package com.example.data.repository

import com.example.data.local.ServerDao
import com.example.data.local.ServerEntity
import com.example.data.model.ServerConfig
import com.example.data.model.ServerSourceType
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(
    private val dao: ServerDao,
    private val cryptoManager: CryptoManager
) {
    val serversFlow: Flow<List<ServerConfig>> = dao.observeServers().map { list ->
        list.map { it.toDomain() }
    }

    val totalServerCountFlow: Flow<Int> = dao.observeTotalCount()

    suspend fun getPagedServers(
        sourceFilter: String = "ALL",
        searchQuery: String = "",
        page: Int,
        pageSize: Int = 50
    ): List<ServerConfig> {
        val safePage = page.coerceAtLeast(0)
        val safeLimit = pageSize.coerceIn(10, 200)
        return dao.queryPaged(
            sourceFilter = sourceFilter,
            searchQuery = searchQuery.trim(),
            limit = safeLimit,
            offset = safePage * safeLimit
        ).map { it.toDomain() }
    }

    suspend fun countFilteredServers(sourceFilter: String = "ALL", searchQuery: String = ""): Int {
        return dao.countFiltered(sourceFilter = sourceFilter, searchQuery = searchQuery.trim())
    }

    suspend fun getSelectedServer(): ServerConfig? {
        return dao.getSelectedServer()?.toDomain()
    }

    suspend fun getServerById(id: Long): ServerConfig? {
        return dao.getServerById(id)?.toDomain()
    }

    suspend fun saveServer(config: ServerConfig, selectAfterSave: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val encryptedPass = cryptoManager.encrypt(config.password)
        val shouldSelect = selectAfterSave || config.isSelected || dao.countServers() == 0

        val entity = ServerEntity(
            id = config.id,
            name = config.name.trim(),
            sshHost = config.sshHost.trim(),
            sshPort = config.sshPort,
            username = config.username.trim(),
            encryptedPassword = encryptedPass,
            tlsEnabled = config.tlsEnabled,
            sniHost = config.sniHost.trim(),
            tlsPort = config.tlsPort,
            customPayload = config.customPayload,
            isSelected = shouldSelect,
            updatedAt = now,
            sourceType = config.sourceType.id,
            region = config.region.trim(),
            catalogUrl = config.catalogUrl.trim(),
            lastLatencyMs = config.lastLatencyMs,
            lastSpeedBps = config.lastSpeedBps,
            lastScore = config.lastScore,
            isFavorite = config.isFavorite
        )

        val savedId = if (config.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            config.id
        }

        if (shouldSelect) {
            dao.selectOnly(savedId)
        }
        return savedId
    }

    suspend fun upsertCloudServers(configs: List<ServerConfig>): Int {
        if (configs.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val entities = configs.map { cfg ->
            val existing = dao.findByEndpoint(cfg.sshHost.trim(), cfg.sshPort, cfg.username.trim())
            ServerEntity(
                id = existing?.id ?: 0L,
                name = cfg.name.trim(),
                sshHost = cfg.sshHost.trim(),
                sshPort = cfg.sshPort,
                username = cfg.username.trim(),
                encryptedPassword = cryptoManager.encrypt(cfg.password),
                tlsEnabled = cfg.tlsEnabled,
                sniHost = cfg.sniHost.trim(),
                tlsPort = cfg.tlsPort,
                customPayload = cfg.customPayload,
                isSelected = existing?.isSelected ?: false,
                updatedAt = now,
                sourceType = cfg.sourceType.id,
                region = cfg.region.trim(),
                catalogUrl = cfg.catalogUrl.trim(),
                lastLatencyMs = existing?.lastLatencyMs ?: cfg.lastLatencyMs,
                lastSpeedBps = existing?.lastSpeedBps ?: cfg.lastSpeedBps,
                lastScore = existing?.lastScore ?: cfg.lastScore,
                isFavorite = existing?.isFavorite ?: cfg.isFavorite
            )
        }
        dao.insertAll(entities)
        return entities.size
    }

    suspend fun selectServer(id: Long) {
        dao.selectOnly(id)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    suspend fun updateBenchmarkMetrics(id: Long, latencyMs: Long, speedBps: Long, score: Int) {
        dao.updateBenchmarkMetrics(
            id = id,
            latencyMs = latencyMs,
            speedBps = speedBps,
            score = score,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun clearCloudCatalogServers() {
        dao.deleteUnselectedBySourceType(ServerSourceType.CLOUD_CATALOG.id)
    }

    suspend fun deleteServer(id: Long) {
        dao.deleteById(id)
    }

    private fun ServerEntity.toDomain(): ServerConfig {
        return ServerConfig(
            id = id,
            name = name,
            sshHost = sshHost,
            sshPort = sshPort,
            username = username,
            password = cryptoManager.decrypt(encryptedPassword),
            tlsEnabled = tlsEnabled,
            sniHost = sniHost,
            tlsPort = tlsPort,
            customPayload = customPayload,
            isSelected = isSelected,
            updatedAt = updatedAt,
            sourceType = ServerSourceType.fromId(sourceType),
            region = region,
            catalogUrl = catalogUrl,
            lastLatencyMs = lastLatencyMs,
            lastSpeedBps = lastSpeedBps,
            lastScore = lastScore,
            isFavorite = isFavorite
        )
    }
}
