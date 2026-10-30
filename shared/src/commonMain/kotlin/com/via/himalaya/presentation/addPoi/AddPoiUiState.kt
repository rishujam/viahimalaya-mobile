package com.via.himalaya.presentation.addPoi

/**
 * Where a place is, and how that was decided. [source] is a LocationSource
 * value; [accuracyM] is only ever set for a GPS fix.
 */
data class PoiLocation(
    val lat: Double,
    val lon: Double,
    val source: String,
    val accuracyM: Double? = null
)

/**
 * The "add a place" form on the trek screen.
 *
 * Held in a ViewModel rather than in the dialog because the dialog goes away
 * while the user places the pin on the map, and has to come back with the photo
 * and the note still there. A rotation does the same.
 */
data class AddPoiUiState(
    /** The flow is active. Everything below only means something while it is. */
    val isOpen: Boolean = false,

    /** The user is placing the pin. The dialog is hidden meanwhile, the form is kept. */
    val isPicking: Boolean = false,

    val trekId: String? = null,

    /** [minLon, minLat, maxLon, maxLat] of the trek the place is being added to. */
    val boundingBox: List<Double> = emptyList(),

    /** The picture as chosen, before it is shrunk. Shrunk and copied only on Save. */
    val photoUri: String? = null,

    val note: String = "",

    val location: PoiLocation? = null,

    /** A fresh GPS fix has been asked for and has not arrived. */
    val isLocating: Boolean = false,

    /** Why there is no usable location, or why the last request for one was refused. */
    val locationMessage: String? = null,

    /** They agreed to this version of the content terms on an earlier save: no checkbox. */
    val termsAgreedBefore: Boolean = false,

    val termsChecked: Boolean = false,

    val isSaving: Boolean = false,

    /** One-shot. The screen shows it as a toast and then asks for it to be cleared. */
    val message: String? = null
) {
    val isDialogVisible: Boolean get() = isOpen && !isPicking

    /**
     * A photo, a place inside the trekking area, and agreement to the terms. The
     * note is optional. Nothing here is checked a second time on Save, so this
     * is the one definition of "complete".
     */
    val canSave: Boolean
        get() = isOpen && !isSaving &&
            photoUri != null &&
            location != null &&
            (termsAgreedBefore || termsChecked)
}
