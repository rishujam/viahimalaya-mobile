package com.via.himalaya.data.local

interface UserPreferences {
    /**
     * Get the user's email
     */
    suspend fun getUserEmail(): String?
    
    /**
     * Save the user's email
     */
    suspend fun saveUserEmail(email: String)
    
    /**
     * Clear the user's email
     */
    suspend fun clearUserEmail()
    
    /**
     * Clear all user preferences
     */
    suspend fun clearAll()
}
