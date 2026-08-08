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
}
