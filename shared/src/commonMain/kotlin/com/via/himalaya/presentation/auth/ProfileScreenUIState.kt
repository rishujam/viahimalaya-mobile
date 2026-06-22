package com.via.himalaya.presentation.auth

import com.via.himalaya.domain.model.UserProfile

data class ProfileScreenUIState(
    val isLoading: Boolean = false,
    val userProfile: UserProfile? = null
)
