package com.via.himalaya

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform