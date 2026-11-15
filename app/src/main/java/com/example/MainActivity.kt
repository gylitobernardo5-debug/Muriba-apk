package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.VpnStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.AdBlockScreen
import com.example.ui.screens.BenchmarkScreen
import com.example.ui.screens.CloudServersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.MuribaMcConnectedScreen
import com.example.ui.screens.SavedServersScreen
import com.example.ui.screens.ServerEditorScreen
import com.example.ui.screens.ServerTestScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MuribaVpnTheme
import com.example.ui.theme.PitchBlack
import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object SavedServers : AppRoute

    @Serializable
    data object CloudServers : AppRoute

    @Serializable
    data object Benchmark : AppRoute

    @Serializable
    data object AdBlock : AppRoute

    @Serializable
    data class ServerEditor(val serverId: Long = -1L) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object Logs : AppRoute

    @Serializable
    data object ServerTest : AppRoute

    @Serializable
    data object MuribaMcConnected : AppRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MuribaVpnTheme {
                MuribaVpnRoot()
            }
        }
    }
}

@Composable
private fun MuribaVpnRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val totalServersCount by viewModel.totalServersCount.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isTestingServer by viewModel.isTestingServer.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val benchmarkResults by viewModel.benchmarkResults.collectAsStateWithLifecycle()
    val isBenchmarkingAll by viewModel.isBenchmarkingAll.collectAsStateWithLifecycle()
    val internalBenchmark by viewModel.internalBenchmark.collectAsStateWithLifecycle()
    val isRunningInternalBenchmark by viewModel.isRunningInternalBenchmark.collectAsStateWithLifecycle()
    val cloudServersPage by viewModel.cloudServersPage.collectAsStateWithLifecycle()
    val cloudFilteredTotal by viewModel.cloudFilteredTotal.collectAsStateWithLifecycle()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsStateWithLifecycle()
    val adBlockUi by viewModel.adBlockUi.collectAsStateWithLifecycle()
    val isSyncingAdBlock by viewModel.isSyncingAdBlock.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val selectedServer = remember(servers) { servers.firstOrNull { it.isSelected } }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val vpnPrepare = viewModel.prepareVpnIntent()
        if (vpnPrepare == null) {
            viewModel.toggleConnection()
        }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnAfterPermissionGranted()
        }
    }

    val handlePowerClick = {
        val status = vpnState.status
        if (status == VpnStatus.CONNECTED ||
            status == VpnStatus.CONNECTING ||
            status == VpnStatus.RECONNECTING
        ) {
            viewModel.toggleConnection()
        } else {
            val prepIntent = viewModel.prepareVpnIntent()
            if (prepIntent != null) {
                vpnPrepareLauncher.launch(prepIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.toggleConnection()
            }
        }
    }

    var lastHandledEventId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vpnState.status, vpnState.muribaScreenEventId) {
        if (vpnState.status == VpnStatus.CONNECTED && vpnState.muribaScreenEventId > lastHandledEventId) {
            lastHandledEventId = vpnState.muribaScreenEventId
            navController.navigate(AppRoute.MuribaMcConnected) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeUiMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PitchBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home
        ) {
            composable<AppRoute.Home> {
                HomeScreen(
                    vpnState = vpnState,
                    selectedServer = selectedServer,
                    totalServersCount = totalServersCount,
                    settings = settings,
                    onPowerClick = handlePowerClick,
                    onOpenServerEditor = { id ->
                        navController.navigate(AppRoute.ServerEditor(serverId = id ?: -1L))
                    },
                    onOpenSavedServers = { navController.navigate(AppRoute.SavedServers) },
                    onOpenCloudServers = { navController.navigate(AppRoute.CloudServers) },
                    onOpenBenchmark = { navController.navigate(AppRoute.Benchmark) },
                    onOpenAdBlock = { navController.navigate(AppRoute.AdBlock) },
                    onOpenSettings = { navController.navigate(AppRoute.Settings) },
                    onOpenLogs = { navController.navigate(AppRoute.Logs) },
                    onOpenServerTest = {
                        viewModel.clearTestResult()
                        navController.navigate(AppRoute.ServerTest)
                    },
                    onOpenMuribaMcScreen = {
                        navController.navigate(AppRoute.MuribaMcConnected) { launchSingleTop = true }
                    },
                    onSelectSpeedMode = viewModel::setSpeedMode,
                    onToggleAutoReconnect = viewModel::setAutoReconnect,
                    onToggleKeepAlive = viewModel::setKeepAlive,
                    onToggleKillSwitch = viewModel::setKillSwitch,
                    onToggleAdBlock = viewModel::setAdBlockEnabled,
                    onSaveCustomDns = viewModel::setCustomDns
                )
            }

            composable<AppRoute.SavedServers> {
                SavedServersScreen(
                    servers = servers,
                    onBack = { navController.popBackStack() },
                    onSelectServer = viewModel::selectServer,
                    onAddServer = { navController.navigate(AppRoute.ServerEditor(serverId = -1L)) },
                    onEditServer = { id -> navController.navigate(AppRoute.ServerEditor(serverId = id)) },
                    onDeleteServer = viewModel::deleteServer
                )
            }

            composable<AppRoute.CloudServers> {
                CloudServersScreen(
                    serversPage = cloudServersPage,
                    totalFiltered = cloudFilteredTotal,
                    totalStored = totalServersCount,
                    savedCatalogUrl = settings.cloudCatalogUrl,
                    isSyncing = isSyncingCloud,
                    isBenchmarking = isBenchmarkingAll,
                    onBack = { navController.popBackStack() },
                    onLoadPage = viewModel::loadCloudPage,
                    onSyncCatalogUrl = viewModel::syncCloudCatalogUrl,
                    onImportJson = viewModel::importCloudJsonText,
                    onClearCatalog = viewModel::clearCloudCatalog,
                    onAutoSelectBest = viewModel::runSmartBenchmarkAndSelectBest,
                    onSelectServer = viewModel::selectServer,
                    onToggleFavorite = viewModel::toggleFavoriteServer,
                    onAddServer = { navController.navigate(AppRoute.ServerEditor(serverId = -1L)) },
                    onEditServer = { id -> navController.navigate(AppRoute.ServerEditor(serverId = id)) },
                    onDeleteServer = viewModel::deleteServer
                )
            }

            composable<AppRoute.Benchmark> {
                BenchmarkScreen(
                    vpnState = vpnState,
                    selectedServer = selectedServer,
                    activeMode = settings.speedMode,
                    benchmarkResult = internalBenchmark,
                    isRunning = isRunningInternalBenchmark,
                    onBack = { navController.popBackStack() },
                    onRunBenchmark = viewModel::runInternalV4Benchmark
                )
            }

            composable<AppRoute.ServerEditor> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.ServerEditor>()
                val editId = if (route.serverId > 0L) route.serverId else null
                ServerEditorScreen(
                    serverId = editId,
                    loadServer = { id -> viewModel.getServerById(id) },
                    onBack = { navController.popBackStack() },
                    onSave = { config ->
                        viewModel.saveServer(config, selectAfterSave = true) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable<AppRoute.AdBlock> {
                AdBlockScreen(
                    settings = settings,
                    adBlockUi = adBlockUi,
                    blockedAdsCount = vpnState.blockedAdsCount,
                    isSyncing = isSyncingAdBlock,
                    onBack = { navController.popBackStack() },
                    onRefresh = viewModel::refreshAdBlockStats,
                    onToggleEnabled = viewModel::setAdBlockEnabled,
                    onToggleAggressive = viewModel::setAdBlockAggressive,
                    onToggleBlockDoh = viewModel::setAdBlockBlockDoh,
                    onToggleBlockSniHttp = viewModel::setAdBlockBlockSniHttp,
                    onSyncUrl = viewModel::syncAdBlockList,
                    onClearExternal = viewModel::clearExternalAdBlockRules,
                    onAddBlacklist = viewModel::addCustomAdBlockDomain,
                    onRemoveBlacklist = viewModel::removeCustomAdBlockDomain,
                    onAddWhitelist = viewModel::addWhitelistDomain,
                    onRemoveWhitelist = viewModel::removeWhitelistDomain,
                    onClearRecent = viewModel::clearRecentAdBlockEvents
                )
            }

            composable<AppRoute.Settings> {
                SettingsScreen(
                    settings = settings,
                    blockedAdsCount = vpnState.blockedAdsCount,
                    onBack = { navController.popBackStack() },
                    onSelectSpeedMode = viewModel::setSpeedMode,
                    onSaveCustomMtu = viewModel::setCustomMtu,
                    onToggleAutoReconnect = viewModel::setAutoReconnect,
                    onToggleKeepAlive = viewModel::setKeepAlive,
                    onToggleKillSwitch = viewModel::setKillSwitch,
                    onToggleAdBlock = viewModel::setAdBlockEnabled,
                    onToggleAdBlockAggressive = viewModel::setAdBlockAggressive,
                    onOpenAdBlock = { navController.navigate(AppRoute.AdBlock) },
                    onToggleReconnectOnNetRestore = viewModel::setReconnectOnNetworkRestore,
                    onSaveCustomDns = viewModel::setCustomDns
                )
            }

            composable<AppRoute.Logs> {
                LogsScreen(
                    logs = logs,
                    onBack = { navController.popBackStack() },
                    onClearLogs = viewModel::clearLogs
                )
            }

            composable<AppRoute.ServerTest> {
                ServerTestScreen(
                    selectedServer = selectedServer,
                    serversCount = servers.size,
                    isTesting = isTestingServer,
                    isBenchmarkingAll = isBenchmarkingAll,
                    result = testResult,
                    benchmarkResults = benchmarkResults,
                    onBack = { navController.popBackStack() },
                    onRunTest = viewModel::runServerTest,
                    onRunSmartBenchmarkAll = viewModel::runSmartBenchmarkAndSelectBest
                )
            }

            composable<AppRoute.MuribaMcConnected> {
                MuribaMcConnectedScreen(
                    vpnState = vpnState,
                    onContinueToDashboard = { navController.popBackStack() },
                    onDisconnect = {
                        viewModel.toggleConnection()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
