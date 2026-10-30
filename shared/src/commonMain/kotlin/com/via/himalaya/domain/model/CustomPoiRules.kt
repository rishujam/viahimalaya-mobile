package com.via.himalaya.domain.model

/**
 * The rules for adding a place that the app and the server both hold. The app
 * uses them to stop the user early; the server's copies are the ones that count
 * (web repo, lib/customPoi.ts). The area margin is in [TrekArea].
 */
object CustomPoiRules {

    const val MAX_NOTE_CHARS = 300

    /**
     * The version of the content terms this build asks users to agree to. It is
     * stored with every submission as the record of what they agreed to, and
     * remembered on the phone so the checkbox is asked once per version.
     *
     * Change it whenever the text at [CONTENT_TERMS_URL] changes in a way users
     * should agree to again - and only then, because it brings the checkbox back
     * for everyone.
     */
    const val CONTENT_TERMS_VERSION = "2026-10-29"

    const val CONTENT_TERMS_URL = "https://viahimalaya.com/content-terms.html"
}
