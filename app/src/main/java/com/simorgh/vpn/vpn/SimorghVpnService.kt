package com.simorgh.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.simorgh.vpn.MainActivity
import com.simorgh.vpn.R
import com.simorgh.vpn.ServiceLocator
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.core.VpnUiState
import com.simorgh.vpn.core.anticensor.DataPlaneProbe
import com.simorgh.vpn.core.anticensor.FallbackPlanner
import com.simorgh.vpn.core.anticensor.FingerprintRotator
import com.simorgh.vpn.core.engine.EngineRegistry
import com.simorgh.vpn.core.engine.VpnEngine
import com.simorgh.vpn.data.PerAppMode
import com.simorgh.vpn.data.SettingsMirror
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.data.TunnelMode
import com.simorgh.vpn.util.Fmt
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The VPN daemon. Runs in the separate :daemon process (v0.2.1) so an engine
 * crash or a UI kill can never silently take the tunnel down; the UI talks
 * to it through start/stop intents + Messenger IPC (DaemonIpc).
 */
class SimorghVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.simorgh.vpn.action.START"
        const val ACTION_STOP = "com.simorgh.vpn.action.STOP"
        const val ACTION_IPC = "com.simorgh.vpn.action.IPC"
        const val EXTRA_PROFILE_ID = "profileId"
        const val CHANNEL_ID = "simorgh_vpn_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context, profileId: String) {
            val i = Intent(context, SimorghVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            ContextCompat.startForegroundService(context, i)
            DaemonIpc.bind(context) // mirror daemon state into the UI process
        }

        fun stop(context: Context) {
            val i = Intent(context, SimorghVpnService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, i)
            DaemonIpc.bind(context)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opsMutex = Mutex()

    private var tun: ParcelFileDescriptor? = null
    private var engine: VpnEngine? = null
    private var statsJob: Job? = null
    private var lastUp = 0L
    private var lastDown = 0L
    private var wasConnected = false

    // ------------------------------------------------------------------ IPC server

    private val clients = CopyOnWriteArrayList<Messenger>()

    private val serverMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            DaemonIpc.MSG_REGISTER -> {
                msg.replyTo?.let { c ->
                    clients.add(c)
                    // late binders catch up instantly with a full snapshot
                    val snap = Message.obtain(null, DaemonIpc.MSG_STATE)
                        .apply { data = VpnBus.toBundle() }
                    runCatching { c.send(snap) }
                }
                true
            }
            DaemonIpc.MSG_UNREGISTER -> {
                msg.replyTo?.let { clients.remove(it) }
                true
            }
            else -> false
        }
    })

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_IPC) serverMessenger.binder else super.onBind(intent)

    private fun broadcastState(st: VpnUiState) {
        if (clients.isEmpty()) return
        val dead = mutableListOf<Messenger>()
        clients.forEach { c ->
            val m = Message.obtain(null, DaemonIpc.MSG_STATE).apply { data = VpnBus.toBundle(st) }
            try {
                c.send(m)
            } catch (e: RemoteException) {
                dead.add(c)
            }
        }
        if (dead.isNotEmpty()) clients.removeAll(dead)
    }

    private fun broadcastLog(line: String) {
        if (clients.isEmpty()) return
        val dead = mutableListOf<Messenger>()
        clients.forEach { c ->
            val m = Message.obtain(null, DaemonIpc.MSG_LOG).apply {
                data = Bundle().apply { putString("line", line) }
            }
            try {
                c.send(m)
            } catch (e: RemoteException) {
                dead.add(c)
            }
        }
        if (dead.isNotEmpty()) clients.removeAll(dead)
    }

    /** Settings snapshot for this daemon: mirror file (fresh) → cached flow. */
    private fun currentSettings(): SimorghSettings =
        SettingsMirror.read(this) ?: ServiceLocator.settings.value

    override fun onCreate() {
        super.onCreate()
        createChannel()
        VpnBus.onState = { broadcastState(it) }
        LogBuffer.onLine = { broadcastLog(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                enterForeground(getString(R.string.notif_stopped), "")
                scope.launch { opsMutex.withLock { stopVpnLocked() } }
            }
            else -> {
                if (intent?.action == VpnService.SERVICE_INTERFACE) {
                    LogBuffer.i("Vpn", "Always-On: system requested the VPN")
                }
                enterForeground(getString(R.string.notif_connecting), "")
                val pid = intent?.getStringExtra(EXTRA_PROFILE_ID)
                scope.launch { opsMutex.withLock { startVpnLocked(pid) } }
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ start/stop

    private suspend fun startVpnLocked(profileId: String?) {
        try {
            // full cleanup of any previous state (also clears a kill-switch hold)
            stopEngineLocked()
            wasConnected = false

            val settings = currentSettings()
            runCatching { ServiceLocator.profileRepo.load() } // fresh read (long-lived daemon)
            val all = ServiceLocator.profileRepo.profiles.value
            val primary = all.firstOrNull { it.id == profileId }
                ?: all.firstOrNull { it.id == settings.activeProfileId }
                ?: all.firstOrNull()
                ?: error("no profile configured")

            VpnBus.update {
                it.copy(
                    status = VpnStatus.CONNECTING,
                    profileId = primary.id,
                    profileName = primary.name,
                    sinceMs = 0,
                    error = ""
                )
            }
            enterForeground(getString(R.string.notif_connecting), primary.name)

            val needsTun = settings.tunnelMode != TunnelMode.PROXY_ONLY

            // v0.3 anti-censor: attempt plan (nova/msn-guard fallback chain).
            // Degenerates to a single attempt with both switches off — exact v0.2 path.
            val plan = FallbackPlanner.plan(primary, all, settings)
            val probe = settings.utlsRotation || settings.fallbackChain
            if (plan.size > 1) {
                LogBuffer.i("Vpn", "anti-censor plan: ${plan.size} attempts (rotation=${settings.utlsRotation} chain=${settings.fallbackChain})")
            }

            var winner: FallbackPlanner.Attempt? = null
            for ((i, attempt) in plan.withIndex()) {
                if (i > 0) {
                    LogBuffer.w("Vpn", "attempt ${i + 1}/${plan.size}: ${attempt.label}")
                    VpnBus.update { it.copy(profileId = attempt.profile.id, profileName = attempt.profile.name) }
                } else {
                    LogBuffer.i("Vpn", "connecting: ${attempt.label}")
                }

                // fresh TUN per attempt (v2rayNG pattern): the Go core takes fd
                // ownership in TUN mode and hev may close it on stop — a reused
                // fd would be a silent EBADF on the next attempt.
                runCatching { tun?.close() }
                tun = if (needsTun) establishTun(settings) else null

                val attemptSettings =
                    if (attempt.fingerprint.isBlank()) settings
                    else settings.copy(fingerprintOverride = attempt.fingerprint)

                // engine registry (mlmvpn pattern): instantiate per attempt
                val engineId = settings.engineId.ifBlank { EngineRegistry.defaultId() }
                val eng = EngineRegistry.create(engineId) ?: error("engine '$engineId' not registered")
                var started = false
                try {
                    started = eng.start(this, attempt.profile, attemptSettings, tun)
                } catch (t: Throwable) {
                    LogBuffer.w("Vpn", "engine start threw: ${t.message}")
                }

                if (started) {
                    val alive = !probe || DataPlaneProbe.ok(settings.socksPort)
                    if (alive) {
                        engine = eng
                        winner = attempt
                        if (attempt.fingerprintIndex >= 0) {
                            // this fingerprint survived a real data-plane request
                            FingerprintRotator.rememberGood(attempt.fingerprintIndex)
                        }
                        break
                    }
                    LogBuffer.w("Vpn", "data-plane probe failed for ${attempt.label} — rotating")
                } else {
                    LogBuffer.w("Vpn", "engine failed to start for ${attempt.label}")
                }
                // teardown this attempt only (tun stays up for the next one)
                runCatching { eng.stopAwait(3000) }
            }

            val win = winner ?: error("all ${plan.size} connect attempts failed")
            val profile = win.profile
            engine ?: error("engine lost after successful attempt")

            if (win.isFallback) {
                // the user should see which profile actually carries the tunnel
                LogBuffer.i("Vpn", "fallback connected via: ${profile.name}")
            }

            lastUp = 0L
            lastDown = 0L
            wasConnected = true
            VpnBus.update {
                it.copy(
                    status = VpnStatus.CONNECTED,
                    profileId = profile.id,
                    profileName = profile.name,
                    sinceMs = System.currentTimeMillis()
                )
            }
            enterForeground(getString(R.string.notif_connected), profile.name)
            LogBuffer.i(
                "Vpn",
                "connected: ${profile.name} [${profile.protocol}/${profile.network}/${profile.security}] mode=${settings.tunnelMode}" +
                    if (win.fingerprint.isNotBlank()) " fp=${win.fingerprint}" else ""
            )

            statsJob = scope.launch { statsLoop() }
        } catch (t: Throwable) {
            LogBuffer.e("Vpn", "start failed", t)
            failLocked(t)
        }
    }

    /** Engine failure: kill-switch hold if enabled+connected, else normal error teardown. */
    private suspend fun failLocked(t: Throwable) {
        val settings = currentSettings() // fresh kill-switch decision
        val hold = wasConnected && settings.killSwitch && tun != null
        if (hold) {
            holdBlackholeLocked(t)
            return
        }
        stopEngineLocked()
        wasConnected = false
        VpnBus.update { it.copy(status = VpnStatus.ERROR, error = t.message ?: "unknown error") }
        enterForeground(getString(R.string.status_error), t.message ?: "")
        delay(3500)
        VpnBus.reset(VpnStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Kill-switch (msn-guard/Derafsh "block on disconnect" pattern): when the
     * engine dies unexpectedly after a successful connection, keep the TUN
     * interface up with no reader — traffic black-holes instead of leaking
     * to the direct (censored) network. The user stops or reconnects manually.
     */
    private suspend fun holdBlackholeLocked(cause: Throwable) {
        LogBuffer.w("Vpn", "kill-switch engaged — holding TUN black-hole: ${cause.message}")
        statsJob?.cancel()
        statsJob = null
        engine?.stopAwait()
        engine = null
        // tun stays open on purpose — that is the whole point
        wasConnected = false
        VpnBus.update {
            it.copy(status = VpnStatus.BLOCKED, error = cause.message ?: "engine stopped")
        }
        enterForeground(getString(R.string.notif_blocked), "")
    }

    private suspend fun stopVpnLocked() {
        VpnBus.update { it.copy(status = VpnStatus.STOPPING) }
        wasConnected = false
        stopEngineLocked()
        VpnBus.reset(VpnStatus.DISCONNECTED)
        LogBuffer.i("Vpn", "disconnected")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun stopEngineLocked() {
        statsJob?.cancel()
        statsJob = null
        engine?.stopAwait()
        engine = null
        runCatching { tun?.close() }
        tun = null
    }

    private fun establishTun(s: SimorghSettings): ParcelFileDescriptor? {
        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(s.mtu)
            .addAddress("10.10.14.2", 30)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .setMetered(false)
        if (s.ipv6) {
            b.addAddress("fc00::10:10:14:2", 126)
            b.addRoute("::", 0)
        }

        // per-app proxy (split tunneling) — Derafsh/V2rayNG PerAppProxy pattern
        val pkgs = s.perAppPackages
        when {
            s.perAppMode == PerAppMode.ALLOW && pkgs.isNotEmpty() -> {
                pkgs.forEach { p ->
                    runCatching { b.addAllowedApplication(p) }
                        .onFailure { LogBuffer.w("Vpn", "allow $p: ${it.message}") }
                }
                LogBuffer.i("Vpn", "per-app: allow-list (${pkgs.size} apps)")
            }
            s.perAppMode == PerAppMode.DENY && pkgs.isNotEmpty() -> {
                try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
                pkgs.forEach { p ->
                    runCatching { b.addDisallowedApplication(p) }
                        .onFailure { LogBuffer.w("Vpn", "deny $p: ${it.message}") }
                }
                LogBuffer.i("Vpn", "per-app: deny-list (${pkgs.size} apps)")
            }
            else -> {
                try {
                    b.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    LogBuffer.w("Vpn", "addDisallowedApplication: ${e.message}")
                }
            }
        }

        val t = b.establish() ?: error("VPN establish failed (permission revoked?)")
        LogBuffer.i("Vpn", "tun established mtu=${s.mtu} ipv6=${s.ipv6}")
        return t
    }

    // ------------------------------------------------------------------ stats

    private suspend fun statsLoop() {
        var notifAcc = 0
        while (true) {
            delay(1000)
            val e = engine
            if (e != null && !e.isRunning) {
                // engine died unexpectedly → kill-switch hold or honest error
                scope.launch {
                    opsMutex.withLock {
                        if (engine === e) failLocked(RuntimeException("core exited unexpectedly"))
                    }
                }
                return
            }
            val (up, down) = e?.stats() ?: (0L to 0L)
            val dUp = (up - lastUp).coerceAtLeast(0)
            val dDown = (down - lastDown).coerceAtLeast(0)
            lastUp = up
            lastDown = down
            VpnBus.update {
                it.copy(upSpeedBps = dUp, downSpeedBps = dDown, upTotal = up, downTotal = down)
            }
            if (++notifAcc >= 2 && VpnBus.state.value.status == VpnStatus.CONNECTED) {
                notifAcc = 0
                val line = "↑ ${Fmt.speed(dUp)} · ↓ ${Fmt.speed(dDown)}"
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notif_connected), line)
                )
            }
        }
    }

    // ------------------------------------------------------------------ notification

    private fun notificationManager(): NotificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        notificationManager().createNotificationChannel(ch)
    }

    private fun enterForeground(text: String, subText: String) {
        val n = buildNotification(text, subText)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun buildNotification(text: String, subText: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SimorghVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (subText.isBlank()) text else "$text · $subText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onRevoke() {
        LogBuffer.w("Vpn", "onRevoke — system revoked the VPN")
        scope.launch { opsMutex.withLock { stopVpnLocked() } }
    }

    override fun onDestroy() {
        VpnBus.onState = null
        LogBuffer.onLine = null
        engine?.stop()
        engine = null
        runCatching { tun?.close() }
        tun = null
        scope.cancel()
        super.onDestroy()
    }
}
