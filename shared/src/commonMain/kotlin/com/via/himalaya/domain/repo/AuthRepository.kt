package com.via.himalaya.domain.repo

import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.util.Result

interface AuthRepository {

    /**
     * Exchanges a Google ID token (obtained natively per platform) for a Firebase
     * session and returns the signed-in user.
     */
    suspend fun signInWithGoogle(idToken: String): Result<UserProfile>

    suspend fun signOut()

    suspend fun getCurrentUser(): UserProfile?

    /**
     * Firebase ID token for authenticating API calls, or null when signed out.
     *
     * Fetch this per request rather than storing it: it expires after an hour,
     * so a copy kept in preferences is stale almost immediately. Firebase keeps
     * a long-lived refresh token on disk and mints a new one when needed.
     */
    suspend fun getIdToken(): String?
}
