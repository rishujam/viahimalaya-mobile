package com.via.himalaya.presentation.addPoi

import com.via.himalaya.data.models.LocationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddPoiUiStateTest {

    private val complete = AddPoiUiState(
        isOpen = true,
        photoUri = "content://photo/1",
        location = PoiLocation(27.43, 88.18, LocationSource.GPS, 5.0),
        termsChecked = true
    )

    @Test
    fun aPhotoALocationAndTheTermsAreEnough() {
        assertTrue(complete.canSave)
    }

    @Test
    fun theNoteIsOptional() {
        assertEquals("", complete.note)
        assertTrue(complete.canSave)
    }

    @Test
    fun eachRequiredPartIsRequired() {
        assertFalse(complete.copy(photoUri = null).canSave)
        assertFalse(complete.copy(location = null).canSave)
        assertFalse(complete.copy(termsChecked = false).canSave)
    }

    @Test
    fun termsAgreedToBeforeNeedNoCheckbox() {
        assertTrue(complete.copy(termsChecked = false, termsAgreedBefore = true).canSave)
    }

    @Test
    fun cannotSaveTwiceOrWhileClosed() {
        assertFalse(complete.copy(isSaving = true).canSave)
        assertFalse(complete.copy(isOpen = false).canSave)
    }

    @Test
    fun theDialogIsHiddenWhileThePinIsPlaced() {
        assertTrue(complete.isDialogVisible)
        assertFalse(complete.copy(isPicking = true).isDialogVisible)
        assertFalse(AddPoiUiState().isDialogVisible)
    }
}
