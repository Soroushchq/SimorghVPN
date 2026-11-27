package com.simorgh.vpn.core

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

/** Shared state between the VPN service and the UI (single-process app). */
object VpnBus {
    private val _state = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = _state

    fun update(transform: (VpnUiState) -> VpnUiState) {
        _state.value = transform(_state.value)
    }

    fun reset(status: VpnStatus = VpnStatus.DISCONNECTED, error: String = "") {
        _state.value = VpnUiState(status = status, error = error)
    }
}
