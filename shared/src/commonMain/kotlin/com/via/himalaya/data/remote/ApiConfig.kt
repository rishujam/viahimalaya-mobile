package com.via.himalaya.data.remote

/**
 * Backend host and bearer token, supplied by the platform at Koin startup rather
 * than compiled into commonMain.
 *
 * The key used to be a constant in TrekRepositoryImpl and went to git with every
 * commit. It now comes from local.properties (gitignored) via BuildConfig. Note
 * that this keeps the secret out of source control - it does *not* make it
 * secret at runtime, since BuildConfig fields are plain string constants in the
 * APK. It is a shared key for the whole app, so it identifies the client, never
 * the user.
 */
data class ApiConfig(
    val baseUrl: String,
    val apiKey: String
)
