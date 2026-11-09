package nl.nextbuzz.bricktimer.logic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.nextbuzz.bricktimer.data.AppLanguage
import java.util.Locale

/** Language picked in the app's settings, kept in sync by the Repository. Snapshot state, so every screen re-renders on a switch. */
var appLanguage by mutableStateOf(AppLanguage.SYSTEM)

/** Dutch when chosen in the app, or on SYSTEM when the device (or per-app) language is Dutch; English everywhere else. */
val isDutch: Boolean get() = (appLanguage.tag ?: Locale.getDefault().language) == "nl"

/** Picks the Dutch or English text for the current language. */
fun tr(nl: String, en: String): String = if (isDutch) nl else en

/** Locale for number formatting: decimal comma in Dutch, point in English. */
val numberLocale: Locale get() = if (isDutch) Locale.forLanguageTag("nl-NL") else Locale.US
