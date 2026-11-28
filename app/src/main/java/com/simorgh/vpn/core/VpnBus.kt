package com.simorgh.vpn.core

import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED, STOPPING, ERROR, BLOCKED }

data class VpnUiState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val profileId: String = "",
    val profileName: String = "",
    val upSpeedBps: Long = 0,
    val downSpeedBps: Long = 0,
    val upTotal: Long = 0,
    val downTotal: Long = 0,
    val sinceMs: Long = 0,
    val error: String = ""
)

/**
 * VPN state, shared between the :daemon process (writer) and the UI process
 * (reader). In the daemon, [update]/[reset] also fan out to registered IPC
 * clients through the [onState] hook (set by SimorghVpnService). In the UI
 * process the mirrored state lands in [setState]; [onState] is never set
 * there, so there is no feedback loop.
 */
object VpnBus {
    private val _state = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = _state

    /** Daemon-side broadcast hook (set by SimorghVpnService). */
    @Volatile
    var onState: ((VpnUiState) -> Unit)? = null

    fun update(transform: (VpnUiState) -> VpnUiState) {
        _state.value = transform(_state.value)
        onState?.invoke(_state.value)
    }

    fun reset(status: VpnStatus = VpnStatus.DISCONNECTED, error: String = "") {
        _state.value = VpnUiState(status = status, error = error)
        onState?.invoke(_state.value)
    }

    /** UI-process mirror entry (called from DaemonIpc messages). */
    fun setState(s: VpnUiState) {
        _state.value = s
    }

    // ---------------------------------------------------------- IPC marshalling

    fun toBundle(s: VpnUiState = _state.value): Bundle = Bundle().apply {
        putString("status", s.status.name)
        putString("profileId", s.profileId)
        putString("profileName", s.profileName)
        putLong("upSpeedBps", s.upSpeedBps)
        putLong("downSpeedBps", s.downSpeedBps)
        putLong("upTotal", s.upTotal)
        putLong("downTotal", s.downTotal)
        putLong("sinceMs", s.sinceMs)
        putString("error", s.error)
    }

    fun fromBundle(b: Bundle?): VpnUiState = VpnUiState(
        status = runCatching {
            VpnStatus.valueOf(b?.getString("status") ?: "")
        }.getOrDefault(VpnStatus.DISCONNECTED),
        profileId = b?.getString("profileId") ?: "",
        profileName = b?.getString("profileName") ?: "",
        upSpeedBps = b?.getLong("upSpeedBps") ?: 0L,
        downSpeedBps = b?.getLong("downSpeedBps") ?: 0L,
        upTotal = b?.getLong("upTotal") ?: 0L,
        downTotal = b?.getLong("downTotal") ?: 0L,
        sinceMs = b?.getLong("sinceMs") ?: 0L,
        error = b?.getString("error") ?: ""
    )
}
