package com.via.himalaya.util

object Constants {

    const val GOOGLE_WEB_CLIENT_ID = "401460108528-69t2j2vh01nrc0vq4cinse75bg0b0fm6.apps.googleusercontent.com"

    object Map {
        /**
         * The one style the app renders AND downloads tiles for.
         *
         * These must never diverge. Offline packs are style-specific: tiles
         * fetched for one style do not satisfy another, so a mismatch means the
         * download silently pulls imagery nobody ever looks at and the map comes
         * up blank once the HTTP cache expires.
         *
         * Value equals Mapbox's Style.SATELLITE_STREETS. Kept as a plain string
         * so shared code can reference it without depending on the Android SDK.
         */
        const val STYLE_URI = "mapbox://styles/mapbox/satellite-streets-v12"
    }

    object Events {
        const val API_ERROR = "api_error"
    }

}