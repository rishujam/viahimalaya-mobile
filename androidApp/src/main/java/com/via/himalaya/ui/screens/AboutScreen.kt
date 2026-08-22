package com.via.himalaya.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.via.himalaya.BuildConfig

/**
 * About and data credits.
 *
 * The attribution here is a licence obligation, not decoration. Trail geometry
 * and POIs come from OpenStreetMap under ODbL 1.0, which allows commercial use
 * but requires the credit below; the elevation data carries its own condition.
 * OSM's attribution guidelines accept a credits screen when the map itself
 * cannot carry the text, provided it is reasonably discoverable - which is why
 * this hangs off the Profile menu rather than being buried in a settings tree.
 *
 * If a future screen renders OSM-derived data somewhere this is not reachable
 * from, that screen needs its own credit.
 */
@Composable
fun AboutScreenRoot(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = "About",
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
        )

        Text(
            text = "Free, offline-first navigation for Himalayan alpine trekking. " +
                    "Built for people walking these trails without a guide.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Data sources",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        CreditCard(
            title = "OpenStreetMap",
            body = "Trail geometry and points of interest are © OpenStreetMap " +
                    "contributors, available under the Open Database License (ODbL 1.0).",
            linkLabel = "openstreetmap.org/copyright",
            url = "https://www.openstreetmap.org/copyright",
            context = context
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Verbatim from the Copernicus DEM licence, Article 6(b) - the notice for
        // data that has been "adapted or modified". Our profiles are resampled at
        // 100 m from the source grid, so 6(a)'s unmodified wording does not apply.
        // Article 6(c) then requires the second sentence word for word. Neither is
        // ours to paraphrase, tighten or re-punctuate.
        CreditCard(
            title = "Copernicus WorldDEM-30",
            body = "Elevation data: produced using Copernicus WorldDEM-30 " +
                    "© DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH " +
                    "2014-2018 provided under COPERNICUS by the European Union " +
                    "and ESA; all rights reserved.\n\n" +
                    "The organisations in charge of the Copernicus programme by law " +
                    "or by delegation do not incur any liability for any use of the " +
                    "Copernicus WorldDEM-30.",
            linkLabel = "spacedata.copernicus.eu",
            url = "https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model",
            context = context
        )

        Spacer(modifier = Modifier.height(12.dp))

        CreditCard(
            title = "Mapbox",
            body = "Map rendering and offline tiles are provided by Mapbox.",
            linkLabel = "mapbox.com/about/maps",
            url = "https://www.mapbox.com/about/maps/",
            context = context
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CreditCard(
    title: String,
    body: String,
    linkLabel: String,
    url: String,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = linkLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable { openCreditLink(context, url) }
            )
        }
    }
}

/**
 * Same Custom Tab treatment as the trek write-up link: the user stays inside the
 * app's task and back returns here. Falls back to any installed browser.
 */
private fun openCreditLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e2: Exception) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    AboutScreenRoot(onBack = {})
}
