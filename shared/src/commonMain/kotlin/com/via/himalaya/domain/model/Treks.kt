package com.via.himalaya.domain.model

import com.via.himalaya.data.models.Trek

data class Treks(
    val treks: List<Trek>,
    val page: Page
)
