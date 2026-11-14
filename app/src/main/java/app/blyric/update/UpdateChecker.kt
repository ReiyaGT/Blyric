package app.blyric.update

import app.blyric.BuildConfig
import app.blyric.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class ReleaseInfo(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val release: ReleaseInfo) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/** Cek rilis terbaru di GitHub Releases repo [BuildConfig.GITHUB_REPO] (rilis stabil saja). */
object UpdateChecker {
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val res = Http.get(
                "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json"),
            )
            when {
                res.code == 404 -> return@withContext UpdateResult.Error("Belum ada rilis di GitHub.")
                !res.ok -> return@withContext UpdateResult.Error("GitHub membalas HTTP ${res.code}.")
            }
            val json = JSONObject(res.body)
            val tag = json.getString("tag_name")
            val remote = Semver.parse(tag) ?: return@withContext UpdateResult.Error("Tag rilis '$tag' bukan SemVer.")
            val current = Semver.parse(BuildConfig.VERSION_NAME.removeSuffix("-debug"))
                ?: return@withContext UpdateResult.Error("Versi app tidak valid.")

            if (remote <= current) return@withContext UpdateResult.UpToDate

            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.getString("browser_download_url"); break
                }
            }
            UpdateResult.Available(
                ReleaseInfo(
                    version = tag.removePrefix("v"),
                    notes = json.optString("body"),
                    pageUrl = json.getString("html_url"),
                    apkUrl = apk,
                ),
            )
        } catch (e: IOException) {
            UpdateResult.Error("Tidak bisa terhubung ke GitHub.")
        } catch (e: Exception) {
            UpdateResult.Error("Gagal membaca info rilis.")
        }
    }
}
