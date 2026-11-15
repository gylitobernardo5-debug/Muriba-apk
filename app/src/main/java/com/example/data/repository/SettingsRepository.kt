package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SpeedMode
import com.example.data.model.VpnSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vpnSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "muriba_vpn_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val ADBLOCK_AGGRESSIVE = booleanPreferencesKey("adblock_aggressive")
        val ADBLOCK_BLOCK_DOH = booleanPreferencesKey("adblock_block_doh")
        val ADBLOCK_BLOCK_SNI_HTTP = booleanPreferencesKey("adblock_block_sni_http")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val RECONNECT_ON_NET_RESTORE = booleanPreferencesKey("reconnect_on_net_restore")
        val SPEED_MODE = stringPreferencesKey("speed_mode")
        val CUSTOM_MTU = intPreferencesKey("custom_mtu")
        val CLOUD_CATALOG_URL = stringPreferencesKey("cloud_catalog_url")
    }

    val settingsFlow: Flow<VpnSettings> = context.vpnSettingsDataStore.data.map { prefs ->
        VpnSettings(
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
            keepAlive = prefs[Keys.KEEP_ALIVE] ?: true,
            killSwitch = prefs[Keys.KILL_SWITCH] ?: false,
            adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
            adBlockAggressive = prefs[Keys.ADBLOCK_AGGRESSIVE] ?: true,
            adBlockBlockDoh = prefs[Keys.ADBLOCK_BLOCK_DOH] ?: true,
            adBlockBlockSniHttp = prefs[Keys.ADBLOCK_BLOCK_SNI_HTTP] ?: true,
            customDns = prefs[Keys.CUSTOM_DNS]?.ifBlank { "1.1.1.1" } ?: "1.1.1.1",
            reconnectOnNetworkRestore = prefs[Keys.RECONNECT_ON_NET_RESTORE] ?: true,
            speedMode = SpeedMode.fromId(prefs[Keys.SPEED_MODE]),
            customMtu = prefs[Keys.CUSTOM_MTU] ?: 0,
            cloudCatalogUrl = prefs[Keys.CLOUD_CATALOG_URL].orEmpty()
        )
    }

    suspend fun getCurrentSettings(): VpnSettings = settingsFlow.first()

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setKeepAlive(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.KEEP_ALIVE] = enabled }
    }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setAdBlockAggressive(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.ADBLOCK_AGGRESSIVE] = enabled }
    }

    suspend fun setAdBlockBlockDoh(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.ADBLOCK_BLOCK_DOH] = enabled }
    }

    suspend fun setAdBlockBlockSniHttp(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.ADBLOCK_BLOCK_SNI_HTTP] = enabled }
    }

    suspend fun setCustomDns(dns: String) {
        val cleaned = dns.trim().ifBlank { "1.1.1.1" }
        context.vpnSettingsDataStore.edit { it[Keys.CUSTOM_DNS] = cleaned }
    }

    suspend fun setReconnectOnNetworkRestore(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.RECONNECT_ON_NET_RESTORE] = enabled }
    }

    suspend fun setSpeedMode(mode: SpeedMode) {
        context.vpnSettingsDataStore.edit { it[Keys.SPEED_MODE] = mode.id }
    }

    suspend fun setCustomMtu(mtu: Int) {
        val normalized = if (mtu == 0) 0 else mtu.coerceIn(1200, 1500)
        context.vpnSettingsDataStore.edit { it[Keys.CUSTOM_MTU] = normalized }
    }

    suspend fun setCloudCatalogUrl(url: String) {
        context.vpnSettingsDataStore.edit { it[Keys.CLOUD_CATALOG_URL] = url.trim() }
    }
}
