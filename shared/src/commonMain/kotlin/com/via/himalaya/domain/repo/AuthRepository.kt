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

}
