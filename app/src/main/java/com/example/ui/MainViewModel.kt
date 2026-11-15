package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MuribaVpnApp
import com.example.data.model.LogEntry
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.data.model.VpnSettings
import com.example.data.model.VpnStatus
import com.example.vpn.AdBlockEngine
import com.example.vpn.AdBlockListSync
import com.example.vpn.BenchmarkEngine
import com.example.vpn.BlockedAdEvent
import com.example.vpn.InternalBenchmarkResult
import com.example.vpn.MuribaVpnService
import com.example.vpn.ServerTestResult
import com.example.vpn.ServerTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdBlockUiState(
    val coreCount: Int = AdBlockEngine.coreRuleCount,
    val externalCount: Int = AdBlockEngine.externalRuleCount,
    val blacklistCount: Int = AdBlockEngine.customBlacklistCount,
    val whitelistCount: Int = AdBlockEngine.customWhitelistCount,
    val totalRules: Int = AdBlockEngine.ruleCount,
    val lastListName: String = AdBlockEngine.stats.value.lastListName,
    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
    val recentEvents: List<BlockedAdEvent> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MuribaVpnApp
    private val serverRepo = app.serverRepository
    private val cloudRepo = app.cloudCatalogRepository
    private val settingsRepo = app.settingsRepository
    private val stateHolder = app.vpnStateHolder
    private val logStore = app.logStore

    val servers: StateFlow<List<ServerConfig>> = serverRepo.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalServersCount: StateFlow<Int> = serverRepo.totalServerCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val settings: StateFlow<VpnSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnSettings())

    val vpnState: StateFlow<VpnRuntimeState> = stateHolder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VpnRuntimeState())

    val logs: StateFlow<List<LogEntry>> = logStore.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isTestingServer = MutableStateFlow(false)
    val isTestingServer: StateFlow<Boolean> = _isTestingServer.asStateFlow()

    private val _testResult = MutableStateFlow<ServerTestResult?>(null)
    val testResult: StateFlow<ServerTestResult?> = _testResult.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<List<ServerTestResult>>(emptyList())
    val benchmarkResults: StateFlow<List<ServerTestResult>> = _benchmarkResults.asStateFlow()

    private val _isBenchmarkingAll = MutableStateFlow(false)
    val isBenchmarkingAll: StateFlow<Boolean> = _isBenchmarkingAll.asStateFlow()

    private val _internalBenchmark = MutableStateFlow<InternalBenchmarkResult?>(null)
    val internalBenchmark: StateFlow<InternalBenchmarkResult?> = _internalBenchmark.asStateFlow()

    private val _isRunningInternalBenchmark = MutableStateFlow(false)
    val isRunningInternalBenchmark: StateFlow<Boolean> = _isRunningInternalBenchmark.asStateFlow()

    // Cloud Infinity paginated state
    private val _cloudServersPage = MutableStateFlow<List<ServerConfig>>(emptyList())
    val cloudServersPage: StateFlow<List<ServerConfig>> = _cloudServersPage.asStateFlow()

    private val _cloudFilteredTotal = MutableStateFlow(0)
    val cloudFilteredTotal: StateFlow<Int> = _cloudFilteredTotal.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    private val _adBlockUi = MutableStateFlow(snapshotAdBlockUi())
    val adBlockUi: StateFlow<AdBlockUiState> = _adBlockUi.asStateFlow()

    private val _isSyncingAdBlock = MutableStateFlow(false)
    val isSyncingAdBlock: StateFlow<Boolean> = _isSyncingAdBlock.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        viewModelScope.launch {
            AdBlockEngine.recentBlocked.collect {
                _adBlockUi.value = snapshotAdBlockUi()
            }
        }
        viewModelScope.launch {
            AdBlockEngine.stats.collect {
                _adBlockUi.value = snapshotAdBlockUi()
            }
        }
    }

    private fun snapshotAdBlockUi(): AdBlockUiState = AdBlockUiState(
        coreCount = AdBlockEngine.coreRuleCount,
        externalCount = AdBlockEngine.externalRuleCount,
        blacklistCount = AdBlockEngine.customBlacklistCount,
        whitelistCount = AdBlockEngine.customWhitelistCount,
        totalRules = AdBlockEngine.ruleCount,
        lastListName = AdBlockEngine.stats.value.lastListName,
        blacklist = AdBlockEngine.getCustomBlacklist(),
        whitelist = AdBlockEngine.getCustomWhitelist(),
        recentEvents = AdBlockEngine.getRecentBlockedEvents()
    )

    fun refreshAdBlockStats() {
        _adBlockUi.value = snapshotAdBlockUi()
    }

    fun clearRecentAdBlockEvents() {
        AdBlockEngine.clearRecentEvents()
        _adBlockUi.value = snapshotAdBlockUi()
    }

    fun consumeUiMessage() {
        _uiMessage.value = null
    }

    fun prepareVpnIntent(): Intent? = VpnService.prepare(app)

    fun toggleConnection() {
        val currentStatus = vpnState.value.status
        if (currentStatus == VpnStatus.CONNECTED ||
            currentStatus == VpnStatus.CONNECTING ||
            currentStatus == VpnStatus.RECONNECTING
        ) {
            MuribaVpnService.stopVpn(app)
            return
        }

        val selected = servers.value.firstOrNull { it.isSelected }
        if (selected == null || selected.sshHost.isBlank()) {
            _uiMessage.value = "Adicione e selecione um servidor SSH antes de ligar."
            return
        }
        MuribaVpnService.startVpn(app)
    }

    fun startVpnAfterPermissionGranted() {
        MuribaVpnService.startVpn(app)
    }

    fun saveServer(config: ServerConfig, selectAfterSave: Boolean = true, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            serverRepo.saveServer(config, selectAfterSave = selectAfterSave)
            logStore.info("Servidor '${config.displayTitle}' guardado com encriptação AES-256.")
            onDone()
        }
    }

    fun selectServer(serverId: Long) {
        viewModelScope.launch {
            serverRepo.selectServer(serverId)
            val chosen = serverRepo.getServerById(serverId)
            if (chosen != null) {
                logStore.info("Servidor selecionado: ${chosen.displayTitle} (${chosen.sshHost}:${chosen.effectivePort})")
            }
        }
    }

    fun toggleFavoriteServer(serverId: Long, favorite: Boolean) {
        viewModelScope.launch {
            serverRepo.setFavorite(serverId, favorite)
        }
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverRepo.deleteServer(serverId)
            logStore.info("Servidor removido do armazenamento Room.")
        }
    }

    suspend fun getServerById(id: Long): ServerConfig? = serverRepo.getServerById(id)

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoReconnect(enabled) }
    }

    fun setKeepAlive(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setKeepAlive(enabled) }
    }

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setKillSwitch(enabled)
            logStore.info("Kill Switch ${if (enabled) "ativado" else "desativado"}.")
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAdBlockEnabled(enabled)
            logStore.info("AdBlock Ultra ${if (enabled) "ativado" else "desativado"}.")
        }
    }

    fun setAdBlockAggressive(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAdBlockAggressive(enabled)
            logStore.info(
                if (enabled) "Proteção Ultra (DNS + TLS SNI + HTTP Host + Anti-DoH) ativada."
                else "Modo Padrão (apenas lista exata DNS) selecionado."
            )
        }
    }

    fun setAdBlockBlockDoh(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAdBlockBlockDoh(enabled)
            logStore.info("Anti-Bypass DoT/DoH (porta 853) ${if (enabled) "ativado" else "desativado"}.")
        }
    }

    fun setAdBlockBlockSniHttp(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAdBlockBlockSniHttp(enabled)
            logStore.info("Inspeção Profunda TLS SNI/HTTP ${if (enabled) "ativada" else "desativada"}.")
        }
    }

    fun syncAdBlockList(url: String) {
        viewModelScope.launch {
            _isSyncingAdBlock.value = true
            val res = AdBlockListSync.syncFromUrl(url)
            _adBlockUi.value = snapshotAdBlockUi()
            _uiMessage.value = res.message
            if (res.success) {
                logStore.success("AdBlock Ultra: ${res.message}")
            } else {
                logStore.warn("AdBlock Ultra: ${res.message}")
            }
            _isSyncingAdBlock.value = false
        }
    }

    fun clearExternalAdBlockRules() {
        AdBlockEngine.clearExternalRules()
        _adBlockUi.value = snapshotAdBlockUi()
        _uiMessage.value = "Listas externas removidas. Proteção núcleo embutida mantida."
        logStore.info("Listas externas de AdBlock removidas.")
    }

    fun addCustomAdBlockDomain(domain: String) {
        val added = AdBlockEngine.addCustomBlacklist(domain)
        _adBlockUi.value = snapshotAdBlockUi()
        if (added) {
            val clean = AdBlockEngine.normalizeDomain(domain) ?: domain.trim()
            _uiMessage.value = "Domínio bloqueado: $clean"
            logStore.info("AdBlock blacklist adicionou: $clean")
        } else {
            _uiMessage.value = "Indique um domínio válido (ex: ads.exemplo.com)."
        }
    }

    fun removeCustomAdBlockDomain(domain: String) {
        AdBlockEngine.removeCustomBlacklist(domain)
        _adBlockUi.value = snapshotAdBlockUi()
    }

    fun addWhitelistDomain(domain: String) {
        val added = AdBlockEngine.addWhitelist(domain)
        _adBlockUi.value = snapshotAdBlockUi()
        if (added) {
            val clean = AdBlockEngine.normalizeDomain(domain) ?: domain.trim()
            _uiMessage.value = "Exceção permitida: $clean"
            logStore.info("AdBlock whitelist permitiu: $clean")
        } else {
            _uiMessage.value = "Indique um domínio válido para exceção."
        }
    }

    fun removeWhitelistDomain(domain: String) {
        AdBlockEngine.removeWhitelist(domain)
        _adBlockUi.value = snapshotAdBlockUi()
    }

    fun setCustomDns(dns: String) {
        viewModelScope.launch {
            settingsRepo.setCustomDns(dns)
            logStore.info("DNS atualizado para ${dns.trim().ifBlank { "1.1.1.1" }}.")
        }
    }

    fun setReconnectOnNetworkRestore(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setReconnectOnNetworkRestore(enabled) }
    }

    fun setSpeedMode(mode: SpeedMode) {
        viewModelScope.launch {
            settingsRepo.setSpeedMode(mode)
            logStore.info("Motor V4 alterado para ${mode.label} (${mode.parallelSshSessions} túneis • Janela ${mode.sshLocalWindowBytes / 1024}KB).")
        }
    }

    fun setCustomMtu(mtu: Int) {
        viewModelScope.launch {
            settingsRepo.setCustomMtu(mtu)
            val label = if (mtu == 0) "Automático" else "$mtu"
            logStore.info("MTU configurado para $label.")
        }
    }

    fun runServerTest(config: ServerConfig?) {
        if (config == null || config.sshHost.isBlank()) {
            _uiMessage.value = "Selecione ou configure um servidor para testar."
            return
        }
        viewModelScope.launch {
            _isTestingServer.value = true
            _testResult.value = null
            val result = ServerTester.testServer(config, settings.value.speedMode)
            _testResult.value = result
            if (result.success && config.id > 0L) {
                serverRepo.updateBenchmarkMetrics(
                    id = config.id,
                    latencyMs = result.latencyMs,
                    speedBps = result.transferSpeedBps,
                    score = result.compositeScore
                )
            }
            _isTestingServer.value = false
        }
    }

    fun runSmartBenchmarkAndSelectBest() {
        val allServers = servers.value
        if (allServers.isEmpty()) {
            _uiMessage.value = "Nenhum servidor guardado para comparar."
            return
        }
        viewModelScope.launch {
            _isBenchmarkingAll.value = true
            _benchmarkResults.value = emptyList()
            val ranked = ServerTester.benchmarkAllServers(allServers, settings.value.speedMode)
            _benchmarkResults.value = ranked

            ranked.forEach { res ->
                if (res.success && res.serverId > 0L) {
                    serverRepo.updateBenchmarkMetrics(
                        id = res.serverId,
                        latencyMs = res.latencyMs,
                        speedBps = res.transferSpeedBps,
                        score = res.compositeScore
                    )
                }
            }

            val best = ranked.firstOrNull { it.success }
            if (best != null && best.serverId > 0L) {
                serverRepo.selectServer(best.serverId)
                logStore.success(
                    "Seleção inteligente escolheu '${best.serverName}' (Pontuação ${best.compositeScore}/100, Latência ${best.latencyMs}ms)."
                )
                _uiMessage.value = "Melhor servidor selecionado: ${best.serverName} (${best.compositeScore}/100)"
            } else {
                _uiMessage.value = "Nenhum servidor respondeu com sucesso ao teste."
            }
            _isBenchmarkingAll.value = false
        }
    }

    fun runInternalV4Benchmark() {
        viewModelScope.launch {
            _isRunningInternalBenchmark.value = true
            val selected = serverRepo.getSelectedServer()
            val result = BenchmarkEngine.runFullBenchmark(selected, settings.value.speedMode)
            _internalBenchmark.value = result
            logStore.info(
                String.format(
                    "Benchmark V4: Codec %.1f MB/s (%d pps) • Loopback %.1f MB/s • Gargalo: %s",
                    result.packetCodecMbPerSec,
                    result.packetsPerSecond,
                    result.loopbackTcpMbPerSec,
                    result.bottleneckReport.category.label
                )
            )
            _isRunningInternalBenchmark.value = false
        }
    }

    fun loadCloudPage(sourceFilter: String, query: String, page: Int, pageSize: Int = 40) {
        viewModelScope.launch {
            val total = serverRepo.countFilteredServers(sourceFilter, query)
            _cloudFilteredTotal.value = total
            val items = serverRepo.getPagedServers(sourceFilter, query, page, pageSize)
            _cloudServersPage.value = items
        }
    }

    fun syncCloudCatalogUrl(url: String, sourceFilter: String, query: String) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            settingsRepo.setCloudCatalogUrl(url)
            val res = cloudRepo.syncFromRemoteUrl(url)
            _uiMessage.value = res.message
            if (res.success) {
                logStore.success(res.message)
            } else {
                logStore.warn(res.message)
            }
            loadCloudPage(sourceFilter, query, 0)
            _isSyncingCloud.value = false
        }
    }

    fun importCloudJsonText(rawJson: String, sourceFilter: String, query: String) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            val count = runCatching { cloudRepo.importJsonText(rawJson) }.getOrDefault(0)
            if (count > 0) {
                val msg = "Importados $count servidor(es) JSON para Room offline."
                logStore.success(msg)
                _uiMessage.value = msg
            } else {
                _uiMessage.value = "JSON inválido ou sem servidores SSH válidos."
            }
            loadCloudPage(sourceFilter, query, 0)
            _isSyncingCloud.value = false
        }
    }

    fun clearCloudCatalog(sourceFilter: String, query: String) {
        viewModelScope.launch {
            serverRepo.clearCloudCatalogServers()
            loadCloudPage(sourceFilter, query, 0)
            _uiMessage.value = "Catálogo remoto limpo (servidores locais e ativo mantidos)."
        }
    }

    fun clearTestResult() {
        _testResult.value = null
        _benchmarkResults.value = emptyList()
    }

    fun clearLogs() {
        logStore.clear()
    }
}
