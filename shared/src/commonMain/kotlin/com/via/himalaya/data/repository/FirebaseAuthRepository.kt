package com.via.himalaya.data.repository

import com.via.himalaya.data.local.UserPreferences
import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.util.Result
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider

/**
 * Firebase-backed [AuthRepository] using the GitLive multiplatform SDK, so the
 * same implementation runs on Android and iOS.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val userPreferences: UserPreferences
) : AuthRepository {

    override suspend fun signInWithGoogle(idToken: String): Result<UserProfile> {
        return try {
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = null)
            val user = auth.signInWithCredential(credential).user
                ?: return Result.Error("Google sign-in returned no user.", -1)
            userPreferences.saveUserEmail(user.email.orEmpty())
            Result.Success(user.toUserProfile())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Google sign-in failed.", -1)
        }
    }

    override suspend fun signOut() {
        userPreferences.clearUserEmail()
        auth.signOut()
    }

    override suspend fun getCurrentUser(): String? {
        return userPreferences.getUserEmail()
    }
}

private fun FirebaseUser.toUserProfile(): UserProfile = UserProfile(
    uid = uid,
    email = email.orEmpty(),
    name = displayName.orEmpty(),
    photoUrl = photoURL.orEmpty(),
)
