package com.ifind.app.ble.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object DiscoveringServices : ConnectionState
    data object Ready : ConnectionState
    data object Disconnecting : ConnectionState
    data class Error(val message: String, val statusCode: Int? = null) : ConnectionState

    val isConnected: Boolean
        get() = this is Connected || this is DiscoveringServices || this is Ready

    val isReady: Boolean
        get() = this is Ready
}
