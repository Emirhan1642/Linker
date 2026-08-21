package com.linker.app.core.util

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREFS_NAME = "linker_language_prefs"
        const val KEY_LANGUAGE = "selected_language"
        const val LANG_SYSTEM = "system"
        const val LANG_TR = "tr"
        const val LANG_EN = "en"

        fun createLocalizedContext(baseContext: Context, languageCode: String): Context {
            val locale = when (languageCode) {
                LANG_TR -> Locale("tr", "TR")
                LANG_EN -> Locale("en", "US")
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        baseContext.resources.configuration.locales[0] ?: Locale.getDefault()
                    } else {
                        @Suppress("DEPRECATION")
                        baseContext.resources.configuration.locale ?: Locale.getDefault()
                    }
                }
            }
            Locale.setDefault(locale)

            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            }
            return baseContext.createConfigurationContext(config)
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(getSavedLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun getSavedLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        _currentLanguage.value = languageCode
        applyAppLocales(languageCode)
    }

    private fun applyAppLocales(languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (languageCode == LANG_SYSTEM) {
                    localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    val locale = when (languageCode) {
                        LANG_TR -> Locale("tr", "TR")
                        LANG_EN -> Locale("en", "US")
                        else -> Locale.getDefault()
                    }
                    localeManager?.applicationLocales = LocaleList(locale)
                }
            } catch (e: Exception) {
                // Fallback to AppCompatDelegate
                val appLocales = if (languageCode == LANG_SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageCode)
                }
                AppCompatDelegate.setApplicationLocales(appLocales)
            }
        } else {
            val appLocales = if (languageCode == LANG_SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(appLocales)
        }
    }

    fun getLocalizedContext(baseContext: Context): Context {
        return createLocalizedContext(baseContext, _currentLanguage.value)
    }
}
