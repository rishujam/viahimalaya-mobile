package com.via.himalaya.data.repository

import com.via.himalaya.data.local.UserPreferences
import com.via.himalaya.domain.Tracker
import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.util.Constants
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
    private val userPreferences: UserPreferences,
    private val tracker: Tracker
) : AuthRepository {

    override suspend fun signInWithGoogle(idToken: String): Result<UserProfile> {
        return try {
            val credential = GoogleAuthProvider.credential(idToken = idToken, accessToken = null)
            val user = auth.signInWithCredential(credential).user
                ?: return Result.Error("Google sign-in returned no user.", -1)
            Result.Success(user.toUserProfile())
        } catch (e: Exception) {
            tracker.track(Constants.Events.API_ERROR, mapOf("error" to e.message.toString()))
            Result.Error(e.message ?: "Google sign-in failed.", -1)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Firebase decides whether anyone is signed in, not our preferences.
     *
     * This is still a local read - Firebase restores the session from disk at
     * startup - so it works with no internet. Preferences were the wrong source
     * of truth: they survive a session being revoked or signed out on another
     * device, which left the app looking signed in while every call failed.
     * They stay on as a fallback for the display fields only.
     *
     * It has to stay a local read, because it runs on every cold start - from
     * Application.onCreate and from AuthViewModel - and offline is the app's
     * normal condition on the trail. Do not fetch an ID token here: it used to,
     * and `getIdToken(false)` only returns the cached token while it is valid
     * (an hour). Once it has expired Firebase needs the network, and with none
     * it throws, which crashed the app on launch.
     */
    override suspend fun getCurrentUser(): UserProfile? {
        val user = auth.currentUser ?: return null
        return UserProfile(
            email = user.email.orEmpty(),
            name = user.displayName,
            photoUrl = user.photoURL.orEmpty()
        )
    }

    /**
     * `false` means "use the cached token unless it is about to expire", so this
     * costs nothing while the token is fresh. Forcing a refresh would put a
     * network round trip in front of every API call.
     *
     * Once the token has expired (an hour) this needs the network, and with none
     * it throws. Nothing calls it yet; the first caller must handle that, and must
     * not run it on a startup path (see [getCurrentUser]).
     */
    override suspend fun getIdToken(): String? = auth.currentUser?.getIdToken(false)
}

private fun FirebaseUser.toUserProfile(): UserProfile = UserProfile(
    email = email.orEmpty(),
    name = displayName.orEmpty(),
    photoUrl = photoURL.orEmpty()
)
