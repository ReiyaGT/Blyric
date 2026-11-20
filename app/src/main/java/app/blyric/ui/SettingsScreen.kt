package app.blyric.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blyric.BuildConfig
import app.blyric.Prefs
import app.blyric.media.hasNotificationAccess
import app.blyric.update.UpdateChecker
import app.blyric.update.UpdateResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    val keepScreenOn by Prefs.keepScreenOn.collectAsStateWithLifecycle()
    val romanize by Prefs.romanize.collectAsStateWithLifecycle()

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    // Transparan: gradient dinamis dari MainActivity harus terlihat sampai ke belakang status/navigation bar.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Akses notifikasi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (hasNotificationAccess(context)) "Aktif — Blyric bisa membaca lagu yang diputar"
                        else "Belum aktif — dibutuhkan untuk mendeteksi lagu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Buka") }
            }

            SettingToggle(
                title = "Jaga layar tetap menyala",
                subtitle = "Layar tidak padam selama ada lagu yang diputar dan Blyric sedang terbuka",
                checked = keepScreenOn,
                onCheckedChange = { Prefs.setKeepScreenOn(it) },
            )

            SettingToggle(
                title = "Romanisasi lirik",
                subtitle = "Tampilkan cara baca huruf Latin di bawah lirik berhuruf non-Latin " +
                    "(Jepang, Korea, Mandarin, Rusia, Arab, Thai, dll.)",
                checked = romanize,
                onCheckedChange = { Prefs.setRomanize(it) },
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Versi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Blyric ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            result = UpdateChecker.check()
                            checking = false
                        }
                    },
                ) { Text(if (checking) "Memeriksa…" else "Cek update") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Lirik berasal dari Better Lyrics dan LRCLIB. Blyric bukan produk resmi Better Lyrics, " +
                        "Musixmatch, Spotify, YouTube Music, atau Poweramp. Open source (GPL-3.0).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { open("https://github.com/${BuildConfig.GITHUB_REPO}") }) { Text("Buka GitHub") }
            }
        }
    }

    when (val r = result) {
        is UpdateResult.Available -> AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Update tersedia: v${r.release.version}") },
            text = { Text(r.release.notes.take(600).ifBlank { "Versi baru tersedia." }) },
            confirmButton = {
                TextButton(onClick = { open(r.release.apkUrl ?: r.release.pageUrl); result = null }) { Text("Unduh") }
            },
            dismissButton = { TextButton(onClick = { result = null }) { Text("Nanti") } },
        )
        UpdateResult.UpToDate -> AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Sudah versi terbaru") },
            text = { Text("Blyric ${BuildConfig.VERSION_NAME} adalah versi terbaru.") },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
        )
        is UpdateResult.Error -> AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Gagal cek update") },
            text = { Text(r.message) },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
        )
        null -> Unit
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
