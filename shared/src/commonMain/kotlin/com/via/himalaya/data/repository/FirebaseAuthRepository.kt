package com.via.himalaya.data.repository

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
    private val auth: FirebaseAuth
) : AuthRepository {

    override val currentUser: UserProfile?
        get() = auth.currentUser?.toUserProfile()

    override suspend fun signInWithGoogle(idToken: String): Result<UserProfile> {
        return try {
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = null)
            val user = auth.signInWithCredential(credential).user
                ?: return Result.Error("Google sign-in returned no user.", -1)
            Result.Success(user.toUserProfile())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Google sign-in failed.", -1)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

private fun FirebaseUser.toUserProfile(): UserProfile = UserProfile(
    uid = uid,
    email = email.orEmpty(),
    name = displayName.orEmpty(),
    photoUrl = photoURL.orEmpty(),
)
