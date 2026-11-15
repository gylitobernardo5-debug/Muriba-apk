package com.example.data.model

data class VpnSettings(
    val autoReconnect: Boolean = true,
    val keepAlive: Boolean = true,
    val killSwitch: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val adBlockAggressive: Boolean = true,
    val adBlockBlockDoh: Boolean = true,
    val adBlockBlockSniHttp: Boolean = true,
    val customDns: String = "1.1.1.1",
    val reconnectOnNetworkRestore: Boolean = true,
    val speedMode: SpeedMode = SpeedMode.ULTRA_TURBO,
    val customMtu: Int = 0, // 0 = Automático consoante o modo de velocidade
    val cloudCatalogUrl: String = ""
) {
    val effectiveMtu: Int
        get() = if (customMtu in 1200..1500) customMtu else speedMode.defaultMtu
}
