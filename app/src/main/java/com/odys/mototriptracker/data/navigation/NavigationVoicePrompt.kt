package com.odys.mototriptracker.data.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks turn-by-turn prompts with the system voice (prefers Greek when available).
 * Mirrors iOS `NavigationVoicePrompt`.
 */
@Singleton
class NavigationVoicePrompt @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ready = AtomicBoolean(false)
    private var tts: TextToSpeech? = null

    var isEnabled: Boolean
        get() = if (!prefs.contains(KEY_VOICE_ENABLED)) true
        else prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()
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
                engine.language = Locale.US
            } else {
                engine.language = locale
            }
            engine.setSpeechRate(0.95f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            })
            ready.set(true)
            AppLogger.i(AppLogger.Category.UI, "Navigation TTS ready locale=${engine.language}")
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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    private fun preferredLocale(engine: TextToSpeech): Locale {
        val greek = Locale.forLanguageTag("el-GR")
        val greekResult = engine.isLanguageAvailable(greek)
        if (greekResult >= TextToSpeech.LANG_AVAILABLE) return greek
        val device = Locale.getDefault()
        val deviceResult = engine.isLanguageAvailable(device)
        if (deviceResult >= TextToSpeech.LANG_AVAILABLE) return device
        return Locale.US
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_VOICE_ENABLED = "moto_nav_voice_enabled"
    }
}
