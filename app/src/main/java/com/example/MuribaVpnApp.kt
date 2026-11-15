package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.local.MuribaDatabase
import com.example.data.repository.CloudCatalogRepository
import com.example.data.repository.LogStore
import com.example.data.repository.ServerRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VpnStateHolder
import com.example.security.CryptoManager
import com.example.vpn.AdBlockEngine

class MuribaVpnApp : Application() {

    val cryptoManager: CryptoManager by lazy { CryptoManager() }

    val database: MuribaDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            MuribaDatabase::class.java,
            "muriba_vpn.db"
        )
            .addMigrations(MuribaDatabase.MIGRATION_1_2)
            .build()
    }

    val serverRepository: ServerRepository by lazy {
        ServerRepository(
            dao = database.serverDao(),
            cryptoManager = cryptoManager
        )
    }

    val cloudCatalogRepository: CloudCatalogRepository by lazy {
        CloudCatalogRepository(serverRepository = serverRepository)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    val vpnStateHolder: VpnStateHolder by lazy { VpnStateHolder() }

    val logStore: LogStore by lazy { LogStore() }

    override fun onCreate() {
        super.onCreate()
        AdBlockEngine.initialize(applicationContext)
    }
}
