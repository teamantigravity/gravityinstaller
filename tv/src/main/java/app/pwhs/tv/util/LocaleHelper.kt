package app.pwhs.tv.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import androidx.core.content.edit

object LocaleHelper {
    private const val PREFS = "locale_prefs_tv"
    private const val KEY_LANG = "app_language_tv"

    fun getStoredLanguage(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "") ?: ""
    }

    fun setAppLanguage(context: Context, tag: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANG, tag) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
            context.getSystemService(LocaleManager::class.java).applicationLocales = localeList
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = getStoredLanguage(base)
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
