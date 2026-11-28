package com.simorgh.vpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.simorgh.vpn.core.VpnBus
import com.simorgh.vpn.core.VpnStatus
import com.simorgh.vpn.util.LogBuffer

/**
 * Messenger IPC bridge between the UI process and the :daemon process
 * (lightweight mlmvpn/nova multi-process pattern):
 *
 *  - UI (client): binds to the running daemon, registers itself, and mirrors
 *    daemon [VpnBus] state + [LogBuffer] lines into this process.
 *  - Daemon (server side lives in SimorghVpnService): pushes full state
 *    snapshots and log lines to every registered client.
 *
 * Binding uses flags = 0 — it attaches to a *running* service only and never
 * starts the daemon on its own.
 */
object DaemonIpc {

    const val MSG_REGISTER = 1
    const val MSG_UNREGISTER = 2
    const val MSG_STATE = 3
    const val MSG_LOG = 4

    private const val REBIND_DELAY_MS = 800L
    private const val UNBIND_AFTER_DISCONNECT_MS = 1500L

    @Volatile
    private var bound = false
    private var conn: ServiceConnection? = null
    private var appCtx: Context? = null
    private val main = Handler(Looper.getMainLooper())

    private val clientHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_STATE -> {
                handleState(msg)
                true
            }
            MSG_LOG -> {
                msg.data?.getString("line")?.let { LogBuffer.addRemote(it) }
                true
            }
            else -> false
        }
    }

    private val clientMessenger = Messenger(clientHandler)

    /** Bind to the daemon (idempotent; call from the main process). */
    fun bind(context: Context) {
        if (bound) return
        val app = context.applicationContext
        appCtx = app

        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val server = service ?: return
                val m = Message.obtain(null, MSG_REGISTER).apply { replyTo = clientMessenger }
                runCatching { Messenger(server).send(m) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
                conn = null
                // daemon died without a farewell — never lie about its state
                VpnBus.reset(VpnStatus.DISCONNECTED)
                // sticky services may come back; one bounded rebind attempt
                main.postDelayed({ bind(app) }, 2000)
            }
        }

        val i = Intent(app, SimorghVpnService::class.java).setAction(SimorghVpnService.ACTION_IPC)
        val ok = runCatching { app.bindService(i, c, 0) }.getOrDefault(false)
        if (ok) {
            bound = true
            conn = c
        } else {
            // service not up yet (e.g. right after start()) — one delayed retry
            main.postDelayed({
                if (!bound && conn == null) {
                    val ok2 = runCatching { app.bindService(i, c, 0) }.getOrDefault(false)
                    if (ok2) {
                        bound = true
                        conn = c
                    }
                }
            }, REBIND_DELAY_MS)
        }
    }

    private fun handleState(msg: Message) {
        val st = runCatching { VpnBus.fromBundle(msg.data) }.getOrNull() ?: return
        VpnBus.setState(st)
        // let the empty daemon rest once fully disconnected
        if (st.status == VpnStatus.DISCONNECTED) {
            main.postDelayed({
                if (VpnBus.state.value.status == VpnStatus.DISCONNECTED) unbind()
            }, UNBIND_AFTER_DISCONNECT_MS)
        }
    }

    private fun unbind() {
        val c = conn ?: return
        val ctx = appCtx ?: return
        runCatching { ctx.unbindService(c) }
        bound = false
        conn = null
    }
}
