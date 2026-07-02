package com.via.himalaya.domain.model

data class Page(
    val seed: String?,
    val pageNo: Int,
    val hasNext: Boolean,
    val total: Int,
    val totalPages: Int
)
