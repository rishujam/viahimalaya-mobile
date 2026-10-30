package com.via.himalaya.data.local

interface UserPreferences {

    /**
     * The version of the content terms the user last agreed to, or null if they
     * never have. Compared with the version this build asks for, so a change to
     * the terms brings the checkbox back and nothing else does.
     */
    suspend fun getAcceptedContentTerms(): String?

    suspend fun setAcceptedContentTerms(version: String)
}
