package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isSelected DESC, isFavorite DESC, lastScore DESC, updatedAt DESC LIMIT 500")
    fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT COUNT(*) FROM servers")
    fun observeTotalCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM servers
        WHERE (:sourceFilter = 'ALL' OR sourceType = :sourceFilter)
          AND (:searchQuery = '' OR name LIKE '%' || :searchQuery || '%' OR sshHost LIKE '%' || :searchQuery || '%' OR region LIKE '%' || :searchQuery || '%')
        ORDER BY isSelected DESC, isFavorite DESC, lastScore DESC, updatedAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun queryPaged(
        sourceFilter: String,
        searchQuery: String,
        limit: Int,
        offset: Int
    ): List<ServerEntity>

    @Query(
        """
        SELECT COUNT(*) FROM servers
        WHERE (:sourceFilter = 'ALL' OR sourceType = :sourceFilter)
          AND (:searchQuery = '' OR name LIKE '%' || :searchQuery || '%' OR sshHost LIKE '%' || :searchQuery || '%' OR region LIKE '%' || :searchQuery || '%')
        """
    )
    suspend fun countFiltered(sourceFilter: String, searchQuery: String): Int

    @Query("SELECT * FROM servers WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): ServerEntity?

    @Query("SELECT * FROM servers WHERE sshHost = :host AND sshPort = :port AND username = :username LIMIT 1")
    suspend fun findByEndpoint(host: String, port: Int, username: String): ServerEntity?

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun countServers(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ServerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ServerEntity>): List<Long>

    @Update
    suspend fun update(entity: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM servers WHERE sourceType = :sourceType AND isSelected = 0")
    suspend fun deleteUnselectedBySourceType(sourceType: String)

    @Query("UPDATE servers SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE servers SET isSelected = 1 WHERE id = :id")
    suspend fun markSelected(id: Long)

    @Query("UPDATE servers SET lastLatencyMs = :latencyMs, lastSpeedBps = :speedBps, lastScore = :score, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateBenchmarkMetrics(id: Long, latencyMs: Long, speedBps: Long, score: Int, updatedAt: Long)

    @Query("UPDATE servers SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Transaction
    suspend fun selectOnly(id: Long) {
        clearSelection()
        markSelected(id)
    }
}
