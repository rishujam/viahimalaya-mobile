package com.via.himalaya.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.via.himalaya.util.Constants

/**
 * Drives the Android Credential Manager to obtain a Google ID token.
 *
 * The returned token is platform-agnostic and is handed to the shared
 * [com.via.himalaya.domain.repo.AuthRepository] to complete the Firebase sign-in.
 *
 * @throws androidx.credentials.exceptions.GetCredentialException if the user
 *   cancels or no Google credential is available.
 */
suspend fun getGoogleIdToken(context: Context): String {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(Constants.GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val response = credentialManager.getCredential(context, request)
    val credential = response.credential

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    throw IllegalStateException("Unexpected credential type: ${credential.type}")
}
