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
    val banner: AppBanner? = null
)

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
