package com.via.himalaya.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Opens this app's Play Store page: the Play Store app when it is installed, the
 * web page otherwise. Never throws - a phone with neither just stays put.
 */
fun openStoreListing(context: Context) {
    val id = context.packageName
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
}

/**
 * Takes the whole screen when this build is below the server's min_version
 * (app_config `app_update`). Nothing behind it is reachable: there is no close
 * button, and back leaves the app because this replaces the navigation graph.
 */
@Composable
fun UpdateRequiredScreen(message: String?, onUpdate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Update required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message ?: "This version of ViaHimalaya is no longer supported. " +
                "Please update to keep using the app.",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        PrimaryButton(
            text = "Update",
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Offered when this build is below latest_version but not below min_version. */
@Composable
fun UpdateAvailableDialog(message: String?, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(text = "Update available") },
        text = { Text(text = message ?: "A new version of ViaHimalaya is available.") },
        confirmButton = { TextButton(onClick = onUpdate) { Text(text = "Update") } },
        dismissButton = { TextButton(onClick = onLater) { Text(text = "Later") } }
    )
}
