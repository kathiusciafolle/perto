package com.bitchat.android.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Perto: cor principal escolhida pela pessoa nas configurações. */
enum class PertoAccent(val label: String) {
    Roxo("Roxo"),
    VerdeAgua("Verde-água"),
    Coral("Coral"),
}

/**
 * Perto: preferências próprias do app (tema de cor e por quanto tempo o Mural mostra as
 * mensagens). Fica no próprio celular, nada é enviado pela rede.
 */
object PertoPrefs {
    private const val PREFS = "perto_settings"
    private const val KEY_ACCENT = "accent"
    private const val KEY_MURAL_HOURS = "mural_hours"

    /** Opções de tempo do Mural, em horas. 0 = mostrar tudo. */
    val muralHourOptions = listOf(1, 3, 6, 0)

    private val _accent = MutableStateFlow(PertoAccent.Roxo)
    val accent: StateFlow<PertoAccent> = _accent

    private val _muralHours = MutableStateFlow(0)
    val muralHours: StateFlow<Int> = _muralHours

    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _accent.value = runCatching { PertoAccent.valueOf(p.getString(KEY_ACCENT, null)!!) }
            .getOrDefault(PertoAccent.Roxo)
        _muralHours.value = p.getInt(KEY_MURAL_HOURS, 0)
        loaded = true
    }

    fun setAccent(context: Context, accent: PertoAccent) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT, accent.name).apply()
        _accent.value = accent
    }

    fun setMuralHours(context: Context, hours: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MURAL_HOURS, hours).apply()
        _muralHours.value = hours
    }

    fun muralLabel(hours: Int): String = if (hours == 0) "Sempre" else "${hours}h"
}
