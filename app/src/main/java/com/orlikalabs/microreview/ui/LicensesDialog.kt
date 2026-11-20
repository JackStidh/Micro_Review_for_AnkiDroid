package com.orlikalabs.microreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orlikalabs.microreview.LicenseText
import com.orlikalabs.microreview.R
import com.orlikalabs.microreview.ui.aero.AeroDialog
import com.orlikalabs.microreview.ui.aero.AeroText
import com.orlikalabs.microreview.ui.aero.GelButton
import com.orlikalabs.microreview.ui.aero.GelTone

/** A library the app ships with, and its license. Proper names, not translated. */
private class Library(val name: String, val license: String)

/** The libraries in the APK. Update it when a dependency is added or removed in `app/build.gradle.kts`. */
private val LIBRARIES = listOf(
    Library("AnkiDroid API (FlashCardsContract, AddContentApi)", "GNU Lesser General Public License v3.0"),
    Library("Android Jetpack: Core, Activity, ExifInterface, Lifecycle, WorkManager", "Apache License 2.0"),
    Library("Jetpack Compose: UI, Foundation, Animation, Material 3, Material Icons", "Apache License 2.0"),
    Library("Kotlin standard library, kotlinx.coroutines", "Apache License 2.0"),
)

/** Settings › Help › Open-source licenses: the app's license (the project's LICENSE file) and the libraries'. */
@Composable
fun AeroLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Copied from the project's LICENSE at build time (copyLicense in app/build.gradle.kts).
    val appLicense = remember {
        runCatching { context.assets.open("LICENSE.txt").bufferedReader().use { LicenseText.reflow(it.readText()) } }
            .getOrDefault("")
    }
    AeroDialog(
        onDismiss,
        stringResource(R.string.licenses_title),
        stringResource(R.string.kanji_about),
        buttons = { GelButton(stringResource(R.string.close), onDismiss, tone = GelTone.NEUTRAL, compact = true) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_name), style = AeroText.title(15))
            Text(appLicense, style = AeroText.soft(12))
            Text(stringResource(R.string.licenses_libraries), style = AeroText.title(15))
            LIBRARIES.forEach {
                Column {
                    Text(it.name, style = AeroText.body(14))
                    Text(it.license, style = AeroText.soft(12))
                }
            }
        }
    }
}
