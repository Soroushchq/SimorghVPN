package com.simorgh.vpn.util

import android.app.Application
import android.os.Build
import java.io.File

/**
 * Which process of the app we are in. The VPN service runs in :daemon
 * (v0.2.1) so an engine crash or a UI ANR can never take the tunnel down;
 * the UI process talks to it over Messenger IPC.
 */
object Processes {

    val isDaemon: Boolean by lazy { currentName().endsWith(":daemon") }

    private fun currentName(): String =
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { Application.getProcessName() }.getOrDefault("")
        } else {
            // cmdline = "<process-name>\0..." on API 26/27
            runCatching {
                File("/proc/self/cmdline").readText().substringBefore('\u0000')
            }.getOrDefault("")
        }
}
