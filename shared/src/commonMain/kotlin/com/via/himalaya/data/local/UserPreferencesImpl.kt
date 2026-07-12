package com.via.himalaya.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserPreferencesImpl(
    private val dataStore: DataStore<Preferences>
) : UserPreferences {
    
    private companion object {
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val NAME = stringPreferencesKey("name")
    }
    
    override suspend fun getUserEmail(): String? {
        return dataStore.data.map { preferences ->
            preferences[USER_EMAIL_KEY]
        }.first()
    }

    override suspend fun saveUserInfo(email: String, name: String) {
        dataStore.edit { prefrences ->
            prefrences[USER_EMAIL_KEY] = email
            prefrences[NAME] = name
        }
    }

    override suspend fun getName(): String? {
        return dataStore.data.map { preferences ->
            preferences[NAME]
        }.first()
    }

    override suspend fun clearUserEmail() {
        dataStore.edit { preferences ->
            preferences.remove(USER_EMAIL_KEY)
        }
    }
    
    override suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
