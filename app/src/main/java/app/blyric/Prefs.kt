package app.blyric

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengaturan sederhana (SharedPreferences) yang bisa diamati dari Compose lewat StateFlow. */
object Prefs {
    private const val FILE = "blyric_prefs"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_ROMANIZE = "romanize"

    private var sp: SharedPreferences? = null

    private val _keepScreenOn = MutableStateFlow(false)
    private val _romanize = MutableStateFlow(true)

    /** Layar tetap menyala selama ada lagu diputar (dan Blyric terbuka). Default: mati. */
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    /** Tampilkan romanisasi untuk lirik berhuruf non-Latin. Default: nyala. */
    val romanize: StateFlow<Boolean> = _romanize.asStateFlow()

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp = p
        _keepScreenOn.value = p.getBoolean(KEY_KEEP_SCREEN_ON, false)
        _romanize.value = p.getBoolean(KEY_ROMANIZE, true)
    }

    fun setKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
        sp?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, value)?.apply()
    }

    fun setRomanize(value: Boolean) {
        _romanize.value = value
        sp?.edit()?.putBoolean(KEY_ROMANIZE, value)?.apply()
    }
}
