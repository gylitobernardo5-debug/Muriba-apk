package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "servers",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["lastScore"]),
        Index(value = ["isFavorite"])
    ]
)
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sshHost: String,
    val sshPort: Int,
    val username: String,
    val encryptedPassword: String,
    val tlsEnabled: Boolean,
    val sniHost: String,
    val tlsPort: Int,
    val customPayload: String,
    val isSelected: Boolean,
    val updatedAt: Long,
    val sourceType: String = "LOCAL",
    val region: String = "",
    val catalogUrl: String = "",
    val lastLatencyMs: Long? = null,
    val lastSpeedBps: Long = 0L,
    val lastScore: Int = 0,
    val isFavorite: Boolean = false
)
