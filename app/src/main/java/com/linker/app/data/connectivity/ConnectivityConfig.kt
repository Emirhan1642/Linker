package com.linker.app.data.connectivity

data class ConnectivityConfig(
    val stateUpdateDebounceMs: Long = 500L,
    val errorBufferCapacity: Int = 16,
    val enableMetrics: Boolean = true,
    val enableLogging: Boolean = true
)
