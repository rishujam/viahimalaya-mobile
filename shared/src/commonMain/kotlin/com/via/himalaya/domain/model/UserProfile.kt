package com.via.himalaya.domain.model

data class UserProfile(
    val photoUrl: String,
    val name: String,
    val email: String,
    val uid: String,
    val treks: Int = 0,
    val distance: Int = 0
)
