package com.via.himalaya.domain.model

import com.via.himalaya.data.models.Loc

sealed class LocationResponse() {
    data class SettingDisabled(val exception: Exception) : LocationResponse()
    data class ErrorFetchingLocation(val error: String) : LocationResponse()
    data class Location(val loc: Loc) : LocationResponse()
}