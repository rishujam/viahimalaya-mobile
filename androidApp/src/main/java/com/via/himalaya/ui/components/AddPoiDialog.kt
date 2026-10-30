package com.via.himalaya.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.via.himalaya.R
import com.via.himalaya.data.models.LocationSource
import com.via.himalaya.domain.model.CustomPoiRules
import com.via.himalaya.presentation.addPoi.AddPoiUiState
import com.via.himalaya.presentation.addPoi.PoiLocation
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything the "add a place" UI can ask for, in one holder. The button, the
 * dialog and the pin picker all take it, instead of a dozen lambdas each. The
 * defaults do nothing, which is what a preview wants.
 */
data class AddPoiActions(
    val onAddClick: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onEditLocation: () -> Unit = {},
    val onUseCurrentLocation: () -> Unit = {},
    val onTermsChecked: (Boolean) -> Unit = {},
    val onOpenTerms: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancelPicking: () -> Unit = {},
    val onLocationPicked: (lat: Double, lon: Double) -> Unit = { _, _ -> }
)

/** The small round button on the trek screen, styled like the back button across from it. */
@Composable
fun AddPlaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add_place),
            contentDescription = "Add a place",
            tint = if (enabled) Color.Black else Color.Gray,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * The form for adding a place: a photo, a note, where it is, and agreement to
 * the terms.
 *
 * Not dismissed by a tap outside, because a stray tap would throw away a photo
 * and a typed note. State lives in the ViewModel, so this can disappear while
 * the user places the pin and come back as it was.
 */
@Composable
fun AddPoiDialog(
    state: AddPoiUiState,
    actions: AddPoiActions
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) actions.onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                text = "Add a place",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            // The form can be taller than a small phone once the keyboard is up.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PhotoSection(
                    photoUri = state.photoUri,
                    onTakePhoto = actions.onTakePhoto,
                    onPickPhoto = actions.onPickPhoto
                )
                Spacer(modifier = Modifier.height(14.dp))
                NoteField(note = state.note, onNoteChange = actions.onNoteChange)
                Spacer(modifier = Modifier.height(14.dp))
                LocationSection(state = state, actions = actions)
                Spacer(modifier = Modifier.height(10.dp))
                TermsSection(state = state, actions = actions)
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.onSave,
                enabled = state.canSave
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDismiss,
                enabled = !state.isSaving
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun PhotoSection(
    photoUri: String?,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit
) {
    Column {
        if (photoUri != null) {
            val model = remember(photoUri) { Uri.parse(photoUri) }
            AsyncImage(
                model = model,
                contentDescription = "The photo you chose",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoButton(
                text = if (photoUri == null) "Take photo" else "Retake",
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f)
            )
            PhotoButton(
                text = if (photoUri == null) "Choose photo" else "Choose another",
                onClick = onPickPhoto,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PhotoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = {
            Text(
                text = "What is here? Water, space to camp, a view… (optional)",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingText = {
            Text(
                text = "${note.length}/${CustomPoiRules.MAX_NOTE_CHARS}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun LocationSection(
    state: AddPoiUiState,
    actions: AddPoiActions
) {
    val location = state.location
    Column {
        Text(
            text = "Location",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = when {
                location != null -> formatCoordinates(location.lat, location.lon)
                state.isLocating -> "Getting your location…"
                else -> "Not set"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (location != null) {
            Text(
                text = describeSource(location),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.locationMessage?.let { message ->
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = message,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = actions.onEditLocation,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Edit")
            }
            TextButton(
                onClick = actions.onUseCurrentLocation,
                enabled = !state.isLocating,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Use current location")
            }
        }
    }
}

@Composable
private fun TermsSection(
    state: AddPoiUiState,
    actions: AddPoiActions
) {
    if (state.termsAgreedBefore) {
        // Agreed on an earlier save: no checkbox, but still say what saving means.
        Column {
            Text(
                text = "By saving you agree ViaHimalaya may show this photo to other users.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TermsLink(onClick = actions.onOpenTerms)
        }
        return
    }

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = state.termsChecked,
                onCheckedChange = actions.onTermsChecked
            )
            Text(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { actions.onTermsChecked(!state.termsChecked) },
                text = "I took this photo, or have permission to share it, and I agree " +
                    "ViaHimalaya may show it to other users.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row {
            Spacer(modifier = Modifier.width(48.dp))
            TermsLink(onClick = actions.onOpenTerms)
        }
    }
}

@Composable
private fun TermsLink(onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .padding(top = 2.dp)
            .clickable(onClick = onClick),
        text = "Read the Content Terms",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1A73E8),
        textDecoration = TextDecoration.Underline
    )
}

/** Five decimals is about a metre. A fixed locale, so a comma never stands in for the point. */
internal fun formatCoordinates(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.5f, %.5f", lat, lon)

private fun describeSource(location: PoiLocation): String =
    if (location.source == LocationSource.GPS) {
        val accuracy = location.accuracyM?.let { " · about ${it.roundToInt()} m" }.orEmpty()
        "Your current location$accuracy"
    } else {
        "Placed on the map"
    }

@Preview
@Composable
private fun AddPoiDialogPreview() {
    AddPoiDialog(
        state = AddPoiUiState(
            isOpen = true,
            note = "Flat ground by a stream, space for four tents",
            location = PoiLocation(27.4327, 88.18163, LocationSource.GPS, 6.0)
        ),
        actions = AddPoiActions()
    )
}
