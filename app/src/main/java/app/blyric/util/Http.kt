package app.blyric.util

import app.blyric.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

object Http {
    private val userAgent = "Blyric/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.GITHUB_REPO})"

    /** Melempar IOException bila jaringan gagal. Kode non-2xx dikembalikan apa adanya. */
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", userAgent)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            c.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
