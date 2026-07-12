package com.via.himalaya.data.local

interface UserPreferences {
    /**
     * Get the user's email
     */
    suspend fun getUserEmail(): String?

    suspend fun saveUserInfo(email: String, name: String)

    suspend fun getName(): String?
    
    /**
     * Clear the user's email
     */
    suspend fun clearUserEmail()
    
    /**
     * Clear all user preferences
     */
    suspend fun clearAll()
}
