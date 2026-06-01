package com.via.himalaya.resources

import org.jetbrains.compose.resources.ExperimentalResourceApi
import viahimalaya_mobile.shared.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
object ResourceLoader {
    suspend fun loadAbcJsonString(): String {
        return Res.readBytes("files/abc.json").decodeToString()
    }
}