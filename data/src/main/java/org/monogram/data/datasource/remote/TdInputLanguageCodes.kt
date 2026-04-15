package org.monogram.data.datasource.remote

import java.util.Locale

internal fun buildTdInputLanguageCodes(locale: Locale = Locale.getDefault()): Array<String> {
    val tags = linkedSetOf<String>()
    val localeTag = locale.toLanguageTag().takeUnless { it.isBlank() || it == "und" }
    val language = locale.language.takeUnless { it.isBlank() || it == "und" }

    localeTag?.let(tags::add)
    language?.let(tags::add)
    if (language != "en") {
        tags += "en"
    }

    return tags.toTypedArray()
}
