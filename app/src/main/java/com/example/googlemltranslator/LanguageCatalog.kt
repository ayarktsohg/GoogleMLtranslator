package com.example.googlemltranslator

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class AppLanguage(
    val code: String,
    val name: String
)

fun getSupportedLanguages(): List<AppLanguage> {

    val russianLocale =
        Locale.forLanguageTag("ru")

    return TranslateLanguage
        .getAllLanguages()
        .map { code ->

            val locale =
                Locale.forLanguageTag(code)

            val rawName =
                locale.getDisplayLanguage(russianLocale)

            AppLanguage(
                code = code,

                name =
                rawName.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(russianLocale)
                    } else {
                        it.toString()
                    }
                }
            )
        }
        .sortedBy {
            it.name
        }
}