package com.via.himalaya.domain.repo

import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.util.Result

interface AuthRepository {

    /** The currently signed-in user, or null. Read synchronously. */
    val currentUser: UserProfile?

    /**
     * Exchanges a Google ID token (obtained natively per platform) for a Firebase
     * session and returns the signed-in user.
     */
    suspend fun signInWithGoogle(idToken: String): Result<UserProfile>

    suspend fun signOut()
}
