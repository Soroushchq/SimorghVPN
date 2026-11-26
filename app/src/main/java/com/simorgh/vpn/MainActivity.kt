package com.simorgh.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.simorgh.vpn.ui.AppRoot
import com.simorgh.vpn.ui.theme.SimorghTheme
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        maybeRequestNotificationPermission()
        setContent {
            SimorghTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme in SHARE_SCHEMES) {
            ImportBus.enqueue(data.toString())
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private val SHARE_SCHEMES = setOf("vless", "vmess", "trojan", "ss", "simorgh")
    }
}

/** Queue of share links imported via deep links; consumed by the Servers screen. */
object ImportBus {
    private val queue = ConcurrentLinkedQueue<String>()

    fun enqueue(link: String) {
        queue.add(link)
    }

    fun pollAll(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val l = queue.poll() ?: break
            out.add(l)
        }
        return out
    }
}
