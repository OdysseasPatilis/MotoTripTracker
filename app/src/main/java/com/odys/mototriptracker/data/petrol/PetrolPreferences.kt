package com.odys.mototriptracker.data.petrol

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Saved brand order + preferred octane grades for petrol recommendations. */
@Singleton
class PetrolPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferredBrands = MutableStateFlow(readBrands())
    val preferredBrands: StateFlow<List<String>> = _preferredBrands.asStateFlow()

    private val _preferredOctanes = MutableStateFlow(readOctanes())
    val preferredOctanes: StateFlow<Set<Int>> = _preferredOctanes.asStateFlow()

    fun brandRank(rawBrand: String?): Int {
        val brands = _preferredBrands.value
        if (rawBrand.isNullOrBlank()) return brands.size + 5
        val needle = normalize(rawBrand)
        brands.indexOfFirst { normalize(it) == needle }.takeIf { it >= 0 }?.let { return it }
        brands.indexOfFirst {
            val n = normalize(it)
            needle.contains(n) || n.contains(needle)
        }.takeIf { it >= 0 }?.let { return it }
        return brands.size + 5
    }

    fun isPreferredBrand(rawBrand: String?): Boolean =
        brandRank(rawBrand) < _preferredBrands.value.size

    fun toggleBrand(brand: String) {
        _preferredBrands.update { current ->
            if (brand in current) current - brand else current + brand
        }
        persistBrands()
    }

    fun moveBrand(fromIndex: Int, toIndex: Int) {
        _preferredBrands.update { current ->
            if (fromIndex !in current.indices) return@update current
            val mutable = current.toMutableList()
            val item = mutable.removeAt(fromIndex)
            val insertAt = toIndex.coerceIn(0, mutable.size)
            mutable.add(insertAt, item)
            mutable
        }
        persistBrands()
    }

    fun toggleOctane(octane: Int) {
        _preferredOctanes.update { current ->
            if (octane in current) current - octane else current + octane
        }
        persistOctanes()
    }

    private fun readBrands(): List<String> {
        val stored = prefs.getString(KEY_BRANDS, null)
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return stored.ifEmpty { listOf("Shell", "BP") }
    }

    private fun readOctanes(): Set<Int> {
        val stored = prefs.getString(KEY_OCTANES, null)
            ?.split('|')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()
        return stored.ifEmpty { setOf(98, 100) }
    }

    private fun persistBrands() {
        prefs.edit { putString(KEY_BRANDS, _preferredBrands.value.joinToString("|")) }
    }

    private fun persistOctanes() {
        prefs.edit {
            putString(KEY_OCTANES, _preferredOctanes.value.sorted().joinToString("|"))
        }
    }

    companion object {
        private const val PREFS_NAME = "petrol_preferences"
        private const val KEY_BRANDS = "preferred_brands"
        private const val KEY_OCTANES = "preferred_octanes"

        val CATALOG = listOf(
            "Shell", "BP", "EKO", "Avin", "Jet Oil", "Revoil", "Aegean", "Elf", "Total", "Q8", "Esso"
        )

        fun normalize(value: String): String {
            val folded = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return folded.trim().lowercase(Locale.US)
        }
    }
}
