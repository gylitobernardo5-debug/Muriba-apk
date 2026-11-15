package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MuribaVpnApp
import com.example.R
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnSettings
import com.example.data.model.VpnStatus
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

class MuribaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_USER_INITIATED = "extra_user_initiated"

        private const val CHANNEL_ID = "muriba_vpn_status"
        private const val NOTIFICATION_ID = 4041

        fun startVpn(context: Context) {
            val intent = Intent(context, MuribaVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_USER_INITIATED, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, MuribaVpnService::class.java).apply {
                action = ACTION_DISCONNECT
                putExtra(EXTRA_USER_INITIATED, true)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val app by lazy { application as MuribaVpnApp }
    private val serverRepo by lazy { app.serverRepository }
    private val settingsRepo by lazy { app.settingsRepository }
    private val stateHolder by lazy { app.vpnStateHolder }
    private val logStore by lazy { app.logStore }

    private var connectionJob: Job? = null
    private var metricsJob: Job? = null
    private var latencyJob: Job? = null

    @Volatile
    private var sessionPool: SshSessionPool? = null

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    @Volatile
    private var forwarder: TunPacketForwarder? = null

    @Volatile
    private var userRequestedStop = false

    @Volatile
    private var networkHandoverRequested = false

    @Volatile
    private var cachedSettings: VpnSettings = VpnSettings()

    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var networkAvailable = true

    @Volatile
    private var currentNetworkHandle: Long? = null

    @Volatile
    private var currentNetworkLabel: String = "Rede ativa"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                userRequestedStop = true
                logStore.info("Pedido de desconexão recebido.")
                disconnectAndStopService(error = null)
                return START_NOT_STICKY
            }

            ACTION_CONNECT, null -> {
                userRequestedStop = false
                startForegroundSafe("A preparar Motor V4...", VpnStatus.CONNECTING)
                launchConnectionLoop()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        userRequestedStop = true
        logStore.warn("Permissão VPN revogada pelo sistema.")
        disconnectAndStopService(error = "Permissão VPN revogada pelo sistema.")
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterNetworkMonitor()
        cleanupTunnel(keepKillSwitchTun = false)
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun launchConnectionLoop() {
        if (connectionJob?.isActive == true) return

        connectionJob = serviceScope.launch {
            var attempt = 0
            while (isActive && !userRequestedStop) {
                attempt++
                val settings = settingsRepo.getCurrentSettings()
                cachedSettings = settings

                val server = serverRepo.getSelectedServer()
                if (server == null || server.sshHost.isBlank()) {
                    val err = "Nenhum servidor SSH selecionado ou configurado."
                    logStore.error(err)
                    disconnectAndStopService(error = err)
                    return@launch
                }

                val mtu = settings.effectiveMtu
                if (attempt == 1) {
                    stateHolder.setConnecting(
                        serverName = server.displayTitle,
                        serverHost = server.sshHost,
                        speedMode = settings.speedMode,
                        effectiveMtu = mtu,
                        networkLabel = currentNetworkLabel
                    )
                    updateNotification(
                        "A ligar (${settings.speedMode.label}) a ${server.displayTitle}...",
                        VpnStatus.CONNECTING
                    )
                } else {
                    stateHolder.setReconnecting(
                        reason = if (networkHandoverRequested) {
                            "Mudança de rede ($currentNetworkLabel) • A reconectar..."
                        } else {
                            "Tentativa de reconexão #$attempt..."
                        },
                        networkLabel = currentNetworkLabel
                    )
                    updateNotification("A reconectar (#$attempt)...", VpnStatus.RECONNECTING)
                }

                val wasHandover = networkHandoverRequested
                networkHandoverRequested = false

                val errorMsg = establishRealTunnel(server, settings)
                if (userRequestedStop || !isActive) break

                val canAutoReconnect = networkHandoverRequested ||
                    wasHandover ||
                    settings.autoReconnect ||
                    (settings.reconnectOnNetworkRestore && !networkAvailable)

                if (!canAutoReconnect) {
                    disconnectAndStopService(errorMsg ?: "Ligação encerrada.")
                    return@launch
                }

                if (networkHandoverRequested) {
                    logStore.info("Transição de rede detetada ($currentNetworkLabel). A restabelecer túnel...")
                    cleanupTunnel(keepKillSwitchTun = settings.killSwitch)
                    delay(350)
                    continue
                }

                logStore.warn(errorMsg ?: "Túnel interrompido. Auto Reconectar ativo.")
                stateHolder.setReconnecting(errorMsg ?: "A aguardar reconexão...", currentNetworkLabel)
                cleanupTunnel(keepKillSwitchTun = settings.killSwitch)

                if (!networkAvailable && settings.reconnectOnNetworkRestore) {
                    logStore.info("Sem internet. A aguardar retorno de Wi-Fi ou Dados Móveis...")
                    updateNotification("Sem rede • A aguardar internet...", VpnStatus.RECONNECTING)
                    while (isActive && !userRequestedStop && !networkAvailable) {
                        delay(800)
                    }
                    if (!userRequestedStop && isActive) {
                        logStore.info("Rede restaurada ($currentNetworkLabel). A reiniciar túnel...")
                    }
                } else {
                    delay(2_200)
                }
            }
        }
    }

    private suspend fun establishRealTunnel(
        server: ServerConfig,
        settings: VpnSettings
    ): String? {
        var pool: SshSessionPool? = null
        val speedMode = settings.speedMode
        val effectiveMtu = settings.effectiveMtu

        return try {
            if (settings.keepAlive) acquireWakeLock() else releaseWakeLock()

            logStore.info(
                "Motor V4 [${speedMode.label}] MTU $effectiveMtu • Janela SSH ${speedMode.sshLocalWindowBytes / 1024}KB -> " +
                    "${server.sshHost}:${server.effectivePort} " +
                    "(${if (server.tlsEnabled) "SSH+TLS 1.3/SNI" else "SSH Direto"})"
            )

            pool = SshSessionPool.establish(
                server = server,
                speedMode = speedMode,
                keepAlive = settings.keepAlive,
                protectSocket = { sock -> protect(sock) },
                onLog = { msg -> logStore.info(msg) }
            )
            if (!pool.primaryConnected) {
                return "Sessão SSH não ficou ativa."
            }
            sessionPool = pool
            val primary = pool.primarySession
            logStore.success(
                "Túnel SSH pronto (${primary.serverVersion ?: "SSH-2.0"}) • ${pool.activeCount} túnel(is) • ${speedMode.label}"
            )

            val tun = tunInterface ?: establishTunInterface(settings, effectiveMtu)
            if (tun == null) {
                return "Não foi possível criar a interface TUN do sistema."
            }
            tunInterface = tun

            AdBlockEngine.initialize(applicationContext)
            val dnsIp = settings.customDns.trim().ifBlank { "1.1.1.1" }
            val packetForwarder = TunPacketForwarder(
                tunFd = tun,
                sessionPool = pool,
                speedMode = speedMode,
                effectiveMtu = effectiveMtu,
                dnsServerIp = dnsIp,
                adBlockEnabledProvider = { cachedSettings.adBlockEnabled },
                adBlockAggressiveProvider = { cachedSettings.adBlockAggressive },
                adBlockBlockDohProvider = { cachedSettings.adBlockBlockDoh },
                adBlockSniHttpProvider = { cachedSettings.adBlockBlockSniHttp },
                logStore = logStore,
                scope = serviceScope
            )
            forwarder = packetForwarder
            packetForwarder.start()

            val fallbackServerIp = runCatching {
                InetAddress.getByName(server.sshHost.trim()).hostAddress
            }.getOrNull() ?: server.sshHost.trim()

            stateHolder.setConnected(
                serverName = server.displayTitle,
                serverHost = server.sshHost.trim(),
                exitIp = fallbackServerIp,
                speedMode = speedMode,
                effectiveMtu = effectiveMtu,
                networkLabel = currentNetworkLabel,
                initialLatencyMs = pool.sshHandshakeMs,
                sshHandshakeMs = pool.sshHandshakeMs,
                tlsHandshakeMs = pool.tlsHandshakeMs,
                tlsProtocol = pool.tlsProtocol,
                sshCipher = pool.tlsCipher,
                activeSshTunnels = pool.activeCount
            )
            updateNotification("Conectado (${speedMode.label}) • ${server.displayTitle}", VpnStatus.CONNECTED)

            serviceScope.launch {
                val realExitIp = fetchExitIpOverSsh(pool, speedMode)
                if (!realExitIp.isNullOrBlank()) {
                    stateHolder.updateExitIp(realExitIp)
                    logStore.info("IP de saída confirmado: $realExitIp")
                }
            }

            val settingsWatcher = serviceScope.launch {
                settingsRepo.settingsFlow.collect { latest ->
                    cachedSettings = latest
                }
            }

            val connectedAtMs = System.currentTimeMillis()
            startMetricsLoop(connectedAtMs, packetForwarder, pool, server.tlsEnabled, speedMode.label)
            startLatencyProbeLoop(pool, speedMode, dnsIp)

            while (serviceScope.isActive &&
                !userRequestedStop &&
                !networkHandoverRequested &&
                pool.primaryConnected
            ) {
                delay(500)
            }

            settingsWatcher.cancel()
            latencyJob?.cancel()
            metricsJob?.cancel()
            if (userRequestedStop) null else "Sessão SSH encerrada."
        } catch (e: JSchException) {
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("Auth fail", ignoreCase = true) ||
                    msg.contains("Auth cancel", ignoreCase = true) ->
                    "Falha de autenticação SSH: utilizador ou palavra-passe inválidos."
                msg.contains("UnknownHostException", ignoreCase = true) ->
                    "Servidor não encontrado: verifique o Host/IP e a internet."
                msg.contains("timeout", ignoreCase = true) ->
                    "Tempo limite excedido ao ligar ao servidor SSH."
                else -> "Erro SSH: ${msg.ifBlank { "ligação recusada" }}"
            }
            logStore.error(friendly)
            friendly
        } catch (e: Exception) {
            val friendly = "Falha na ligação: ${e.message ?: "erro desconhecido"}"
            logStore.error(friendly)
            friendly
        } finally {
            latencyJob?.cancel()
            metricsJob?.cancel()
            forwarder?.stop()
            forwarder = null
            pool?.disconnectAll()
            sessionPool = null
        }
    }

    private fun establishTunInterface(settings: VpnSettings, effectiveMtu: Int): ParcelFileDescriptor? {
        val dnsIp = settings.customDns.trim().ifBlank { "1.1.1.1" }
        val builder = Builder()
            .setSession("ADM MURIBA VPN SIMPLES")
            .setMtu(effectiveMtu)
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:10:8::2", 128)
            .addRoute("::", 0)
            .addDnsServer(dnsIp)

        runCatching { builder.addDisallowedApplication(packageName) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val pfd = builder.establish()
        if (pfd != null) {
            logStore.info("Ultra TUN ativo (MTU $effectiveMtu • Pool ${settings.speedMode.tunPoolSlots} slots • DNS $dnsIp)")
        }
        return pfd
    }

    private fun fetchExitIpOverSsh(pool: SshSessionPool, speedMode: SpeedMode): String? {
        var channel: ChannelDirectTCPIP? = null
        return try {
            channel = pool.openOptimizedChannel("api.ipify.org", 80, speedMode)
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(5_000)

            val req = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n"
            output.write(req.toByteArray(Charsets.US_ASCII))
            output.flush()

            val reader = BufferedReader(InputStreamReader(input, Charsets.US_ASCII))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && lines.size < 32) {
                lines.add(line!!)
            }
            val body = lines.lastOrNull()?.trim()
            if (body != null && body.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) body else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { channel?.disconnect() }
        }
    }

    private fun startLatencyProbeLoop(pool: SshSessionPool, speedMode: SpeedMode, dnsIp: String) {
        latencyJob?.cancel()
        latencyJob = serviceScope.launch {
            while (isActive && !userRequestedStop && pool.primaryConnected) {
                val rtt = measureTunnelLatency(pool, speedMode, dnsIp)
                if (rtt != null) {
                    stateHolder.updateLatency(rtt)
                }
                delay(6_000)
            }
        }
    }

    private fun measureTunnelLatency(pool: SshSessionPool, speedMode: SpeedMode, targetHost: String): Long? {
        var channel: ChannelDirectTCPIP? = null
        return try {
            val t0 = System.currentTimeMillis()
            channel = pool.openOptimizedChannel(targetHost, 53, speedMode)
            channel.connect(4_000)
            (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { channel?.disconnect() }
        }
    }

    private fun startMetricsLoop(
        connectedAtMs: Long,
        packetForwarder: TunPacketForwarder,
        pool: SshSessionPool,
        tlsEnabled: Boolean,
        modeLabel: String
    ) {
        metricsJob?.cancel()
        metricsJob = serviceScope.launch {
            var prevDown = 0L
            var prevUp = 0L
            var prevPackets = 0L
            while (isActive && !userRequestedStop && pool.primaryConnected) {
                delay(1_000)
                val nowDown = packetForwarder.bytesDownload.get()
                val nowUp = packetForwarder.bytesUpload.get()
                val nowPackets = packetForwarder.totalPacketsProcessed.get()

                val speedDown = (nowDown - prevDown).coerceAtLeast(0L)
                val speedUp = (nowUp - prevUp).coerceAtLeast(0L)
                val pps = (nowPackets - prevPackets).coerceAtLeast(0L)
                prevDown = nowDown
                prevUp = nowUp
                prevPackets = nowPackets

                val elapsedSec = ((System.currentTimeMillis() - connectedAtMs) / 1000L).coerceAtLeast(0L)
                val lossPct = packetForwarder.computePacketLossPercent()
                packetForwarder.tuneAdaptiveParameters(speedDown, lossPct)

                val tunMicros = packetForwarder.sampleTunAvgMicros()
                val cpuPct = packetForwarder.sampleCpuPercent()
                val ramMb = packetForwarder.sampleRamUsedMb()
                val retransmissions = packetForwarder.retransmissionEvents.get()

                val bottleneck = BottleneckDetector.analyze(
                    state = stateHolder.state.value.copy(
                        durationSeconds = elapsedSec,
                        downloadSpeedBps = speedDown,
                        packetLossPercent = lossPct,
                        retransmissions = retransmissions,
                        tunAvgMicros = tunMicros,
                        cpuPercent = cpuPct,
                        ramUsedMb = ramMb,
                        activeSshTunnels = pool.activeCount
                    ),
                    tlsEnabled = tlsEnabled,
                    poolOverflowEvents = packetForwarder.poolOverflowCount
                )

                stateHolder.updateMetrics(
                    durationSeconds = elapsedSec,
                    downloadSpeedBps = speedDown,
                    uploadSpeedBps = speedUp,
                    totalDownloadBytes = nowDown,
                    totalUploadBytes = nowUp,
                    packetLossPercent = lossPct,
                    retransmissions = retransmissions,
                    packetsPerSecond = pps,
                    tunAvgMicros = tunMicros,
                    activeSshTunnels = pool.activeCount,
                    cpuPercent = cpuPct,
                    ramUsedMb = ramMb,
                    blockedAdsCount = packetForwarder.blockedAdsCount.get(),
                    activeConnections = packetForwarder.activeSessionCount,
                    bottleneckSummary = bottleneck.headline,
                    bottleneckRecommendation = bottleneck.recommendation
                )

                val summary = "[$modeLabel] ↓ ${formatRate(speedDown)} ↑ ${formatRate(speedUp)} • ${formatDuration(elapsedSec)}"
                updateNotification(summary, VpnStatus.CONNECTED)
            }
        }
    }

    private fun disconnectAndStopService(error: String?) {
        connectionJob?.cancel()
        metricsJob?.cancel()
        latencyJob?.cancel()
        cleanupTunnel(keepKillSwitchTun = false)
        releaseWakeLock()
        stateHolder.setDisconnected(error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupTunnel(keepKillSwitchTun: Boolean) {
        forwarder?.stop()
        forwarder = null

        sessionPool?.disconnectAll()
        sessionPool = null

        if (!keepKillSwitchTun) {
            runCatching { tunInterface?.close() }
            tunInterface = null
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            cm.activeNetwork?.let { active ->
                currentNetworkHandle = active.networkHandle
                currentNetworkLabel = resolveNetworkLabel(cm.getNetworkCapabilities(active))
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
                    val newHandle = network.networkHandle
                    val newLabel = resolveNetworkLabel(caps)
                    val prevHandle = currentNetworkHandle
                    currentNetworkHandle = newHandle
                    currentNetworkLabel = newLabel
                    networkAvailable = true
                    stateHolder.updateNetworkLabel(newLabel)

                    if (prevHandle != null && prevHandle != newHandle && sessionPool?.primaryConnected == true) {
                        networkHandoverRequested = true
                    }
                }

                override fun onLost(network: Network) {
                    if (network.networkHandle == currentNetworkHandle) {
                        networkAvailable = false
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    networkAvailable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val label = resolveNetworkLabel(caps)
                    currentNetworkLabel = label
                    stateHolder.updateNetworkLabel(label)
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (_: Throwable) {
        }
    }

    private fun resolveNetworkLabel(caps: NetworkCapabilities?): String {
        if (caps == null) return "Rede ativa"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados Móveis"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Rede ativa"
        }
    }

    private fun unregisterNetworkMonitor() {
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
            networkCallback = null
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MuribaVpn::TunnelWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafe(contentText: String, status: VpnStatus) {
        val notification = buildNotification(contentText, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String, status: VpnStatus) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(contentText, status))
    }

    private fun buildNotification(contentText: String, status: VpnStatus): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MuribaVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusTitle = when (status) {
            VpnStatus.CONNECTED -> "ADM MURIBA VPN • CONECTADO"
            VpnStatus.CONNECTING -> "ADM MURIBA VPN • A CONECTAR"
            VpnStatus.RECONNECTING -> "ADM MURIBA VPN • A RECONECTAR"
            VpnStatus.DISCONNECTED -> "ADM MURIBA VPN • DESCONECTADO"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(statusTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_disconnect), disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatRate(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        }
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
