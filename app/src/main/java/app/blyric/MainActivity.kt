package app.blyric

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.blyric.ui.BlyricTheme
import app.blyric.ui.LyricsScreen
import app.blyric.ui.SettingsScreen
import app.blyric.ui.albumBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        // Status bar & navigation bar bawaan sistem dibuat transparan: gradient aplikasi digambar di belakangnya,
        // jadi warna keduanya otomatis mengikuti latar (termasuk saat warna album berubah). Latar selalu gelap
        // (lihat PaletteMath), jadi ikon sistem dipaksa terang.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Tanpa ini, tombol 3-tombol (Back/Home/Recents) diberi scrim hitam/abu-abu oleh sistem (API 29+),
        // dan sebagian perangkat menggambar garis pemisah di atasnya (API 28+).
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT

        setContent { BlyricTheme { BlyricApp() } }
    }
}

@Composable
private fun BlyricApp(vm: LyricsViewModel = viewModel()) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val palette by vm.palette.collectAsStateWithLifecycle()
    BackHandler(enabled = showSettings) { showSettings = false }

    // Latar dipasang di sini (bukan di tiap layar) supaya Lirik dan Pengaturan berbagi gradient yang sama,
    // dan area di belakang system bars selalu berwarna sama dengan halaman.
    Box(Modifier.albumBackground(palette)) {
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            LyricsScreen(vm = vm, onOpenSettings = { showSettings = true })
        }
    }
}
