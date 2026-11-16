package com.via.himalaya.domain.model

/**
 * Remote configuration, fetched once per launch.
 *
 * Every section is nullable and absence means the feature is off. That is what
 * lets the server retire something by omitting it, and it is also the state the
 * app sits in before the fetch returns or when it fails - so "no config yet",
 * "config says no" and "config unreachable" are deliberately the same case.
 */
data class AppConfig(
    val banner: AppBanner? = null,
    /**
     * Switches the server sent, by wire key. Only well-formed booleans make it
     * in; read them through [isEnabled], never directly, so a missing key falls
     * back to the feature's own default.
     */
    val features: Map<String, Boolean> = emptyMap(),
    /** The `app_update` row, or null when the server sent none or nothing usable. */
    val update: AppUpdate? = null
) {
    fun isEnabled(feature: Feature): Boolean = features[feature.key] ?: feature.default

    /**
     * What this build should do about updating. NONE until config arrives, so
     * the blocking screen can only ever appear once the server has asked for it.
     */
    fun updateNeed(currentVersionCode: Int): UpdateNeed {
        val update = update ?: return UpdateNeed.NONE
        return when {
            update.minVersion != null && currentVersionCode < update.minVersion -> UpdateNeed.REQUIRED
            update.latestVersion != null && currentVersionCode < update.latestVersion -> UpdateNeed.OPTIONAL
            else -> UpdateNeed.NONE
        }
    }
}

/**
 * From the `app_update` row of app_config, e.g.
 * `{"min_version": 14, "latest_version": 15, "message": "..."}`. Versions are
 * Android versionCodes, not version names. Either may be absent.
 */
data class AppUpdate(
    /** Builds below this cannot be used: a blocking screen. */
    val minVersion: Int? = null,
    /** Builds below this are offered an update they can dismiss. */
    val latestVersion: Int? = null,
    /** Shown on the screen or the prompt; the app has its own words when null. */
    val message: String? = null
)

enum class UpdateNeed { NONE, OPTIONAL, REQUIRED }

/**
 * Every feature the server can switch on or off, from the `features` row of
 * app_config, e.g. `{"planner": false}`.
 *
 * [default] is what the app does when the switch is missing - before config
 * arrives, offline at launch (config is not cached), or when the row or key is
 * absent. Choose it per feature: off for something being held back, on for a
 * kill switch on something that should normally run.
 *
 * app_config is world-readable (006_create_app_config.sql), so no entry here may
 * name a feature that has not been released.
 */
enum class Feature(val key: String, val default: Boolean) {
    /** Trek planning (HANDOFF section 18). Held back until users ask for it. */
    PLANNER("planner", default = false)
}

/**
 * A config-driven banner. All three fields are required.
 *
 * [action] is a resolved enum rather than the raw string, which is the point:
 * an [AppBanner] cannot exist unless the app understands what tapping it does.
 * See [BannerAction.from].
 */
data class AppBanner(
    val title: String,
    val description: String,
    val action: BannerAction
)

/**
 * What tapping a banner does.
 *
 * Never deserialized directly from the wire. kotlinx.serialization throws on an
 * enum value it does not recognise, and `ignoreUnknownKeys` does not cover that
 * case - it only forgives unknown *object keys*. So decoding this type straight
 * from JSON would mean the first new action shipped to production fails the
 * parse of the entire config response on every install that has not updated,
 * silently disabling config for exactly the users who need it most.
 *
 * The wire type stays a String and resolution happens here instead.
 */
enum class BannerAction {
    REQUEST_TREK_DIALOG;

    companion object {
        /**
         * Resolves a wire value, or null when this build does not know it.
         *
         * Null is not an error to report - it is the ordinary case of an older
         * app meeting a newer server. Callers fail closed and render nothing:
         * a banner that does nothing when tapped is worse than no banner.
         */
        fun from(raw: String?): BannerAction? {
            val name = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == name }
        }
    }
}
