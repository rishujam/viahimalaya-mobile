package com.via.himalaya.data.models

import kotlinx.serialization.Serializable

@Serializable
data class VResponse<T>(
    val success: Boolean,
    val data: T
)
