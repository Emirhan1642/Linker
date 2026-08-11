package com.linker.app.domain.model

data class PoiInfo(
    val id: Long,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double
)
