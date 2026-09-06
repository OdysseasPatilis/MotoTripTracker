package com.odys.mototriptracker.data.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.edit
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks turn-by-turn prompts with the system voice.
 * Uses English because Directions / OSRM step text is English — a Greek TTS voice
 * reading English instructions sounds mismatched.
 */
@Singleton
class NavigationVoicePrompt @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ready = AtomicBoolean(false)
    private var tts: TextToSpeech? = null

    var isEnabled: Boolean
        get() = if (!prefs.contains(KEY_VOICE_ENABLED)) true
        else prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_VOICE_ENABLED, value) }
            if (!value) stop()
        }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                AppLogger.w(AppLogger.Category.UI, "Navigation TTS init failed status=$status")
                ready.set(false)
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val locale = preferredLocale(engine)
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AppLogger.w(AppLogger.Category.UI, "Navigation TTS locale unsupported: $locale")
                engine.setLanguage(Locale.US)
            }
            selectEnglishVoice(engine)
            engine.setSpeechRate(0.95f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            })
            ready.set(true)
            AppLogger.i(
                AppLogger.Category.UI,
                "Navigation TTS ready locale=${engine.voice?.locale ?: locale} voice=${engine.voice?.name}"
            )
        }
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (!isEnabled || trimmed.isEmpty()) return
        val engine = tts
        if (engine == null || !ready.get()) {
            AppLogger.d(AppLogger.Category.UI, "Navigation TTS not ready — skipped: $trimmed")
            return
        }
        engine.stop()
        engine.speak(
            trimmed,
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun stop() {
        tts?.stop()
    }

    private fun preferredLocale(engine: TextToSpeech): Locale {
        val english = listOf(Locale.US, Locale.UK, Locale.ENGLISH)
        for (locale in english) {
            if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) return locale
        }
        val device = Locale.getDefault()
        if (engine.isLanguageAvailable(device) >= TextToSpeech.LANG_AVAILABLE) return device
        return Locale.US
    }

    /** Prefer an on-device English voice so Greek-accented default voices aren't used. */
    private fun selectEnglishVoice(engine: TextToSpeech) {
        val voices = engine.voices ?: return
        val english = voices
            .asSequence()
            .filter { it.locale.language.equals("en", ignoreCase = true) }
            .filter { !it.isNetworkConnectionRequired }
            .filter { it.features?.contains("notInstalled") != true }
            .sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { if (it.locale.country.equals("US", ignoreCase = true)) 0 else 1 }
            )
            .firstOrNull()
        if (english != null) {
            engine.voice = english
        }
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_VOICE_ENABLED = "moto_nav_voice_enabled"
    }
}
