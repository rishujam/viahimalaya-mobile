package com.via.himalaya.domain.model

data class UserProfile(
    val photoUrl: String? = null,
    val name: String? = null,
    val email: String,
    val treks: Int = 0,
    val distance: Int = 0
)
