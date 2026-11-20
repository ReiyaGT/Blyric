<p align="center"><img src="docs/icon-512.png" width="120" alt="Blyric"></p>

# Blyric

Lirik sinkron untuk lagu yang sedang diputar di **app musik apa pun** (Poweramp, YouTube Music, Spotify, dll).
Lirik mengikuti format [Better Lyrics](https://github.com/better-lyrics/better-lyrics): sinkron per baris, per kata/suku kata,
dan vokal latar yang muncul-hilang / tampil bersamaan dengan baris utama.

> Blyric adalah proyek tidak resmi dan tidak berafiliasi dengan Better Lyrics, Musixmatch, Spotify, YouTube Music, atau Poweramp.

## Cara kerja

1. `NotificationListenerService` + `MediaSessionManager` membaca sesi media aktif (judul, artis, durasi, posisi, play/pause). Tanpa mic, tanpa root.
2. Metadata dibersihkan (`TrackNormalizer`), lalu lirik diambil berurutan: **Better Lyrics API** → **LRCLIB** (cadangan). Hasil di-cache.
3. Parser TTML/LRC → model `LyricLine` / `LyricWord`. Posisi lagu dihitung per frame dari snapshot `PlaybackState`, bukan polling.
4. Compose merender lirik: sorot per kata, auto-scroll, tap baris untuk seek, offset ±100 ms.
5. Tombol ⏮ ⏯ ⏭ mengendalikan app musik lewat `MediaController`. Di landscape, panel kontrol ada di kiri dan baris aktif selalu di tengah.
6. Romanisasi (huruf Latin) untuk lirik non-Latin. Lirik Jepang (hiragana, katakana, **dan kanji**) dibaca oleh Kuromoji (kamus IPADIC)
   sesuai konteks kalimat, lalu diubah ke romaji (partikel は/へ/を → wa/e/o). Aksara lain (Kiril, Hangul, Arab, Thai, Han/pinyin, dll.)
   memakai ICU bawaan Android (`android.icu`).
   Batasan: kamus Kuromoji menambah ±10 MB pada APK; kanji di luar kamus (nama/ateji langka) dibiarkan apa adanya;
   Hangul memakai skema ICU, bukan Revised Romanization.
7. Tampilan: latar gradient mengikuti warna sampul album (diekstrak dari artwork sesi media; tema bawaan bila tidak ada), system bar
   transparan sehingga navigation bar menyatu dengan latar, tombol `‹ SUMBER ›` untuk berganti sumber lirik (Better Lyrics / LRCLIB),
   dan lirik di portrait memudar halus (fade + blur ringan di Android 12+) sebelum kontrol playback.
8. Pengaturan: *Jaga layar tetap menyala* (saat lagu diputar) dan *Romanisasi lirik*, keduanya bisa dimatikan.

## Build lokal

Butuh JDK 17 + Android SDK. Tidak ada Gradle wrapper di repo; pakai Gradle 8.9 (`gradle :app:assembleDebug`) atau jalankan
`gradle wrapper --gradle-version 8.9` sekali lalu commit hasilnya.

Ganti `blyric.githubRepo=OWNER/Blyric` di `gradle.properties` dengan repo kamu (dipakai fitur Cek Update).

## Keystore & rilis (SemVer)

**Sekali saja:**

```bash
scripts/generate-keystore.sh            # menghasilkan blyric-release.jks + teks base64
```

Simpan `.jks` dan password di tempat aman (jangan di-commit; sudah di `.gitignore`). Kalau hilang, update tidak bisa dipasang di atas versi lama.
Lalu isi **Settings → Secrets and variables → Actions**: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

**Setiap rilis:**

```bash
scripts/release.sh 0.2.0                # buat tag v0.2.0 + push
```

GitHub Actions menjalankan test, membangun APK signed, memverifikasi tanda tangan, lalu membuat Release berisi
`Blyric-v0.2.0.apk` + `.sha256` + catatan rilis otomatis. Tag dengan akhiran seperti `-beta.1` otomatis jadi *pre-release*
(tidak ditawarkan oleh Cek Update).

| Naik | Kapan |
|------|-------|
| MAJOR (`1.0.0` → `2.0.0`) | perubahan yang merusak kompatibilitas |
| MINOR (`1.0.0` → `1.1.0`) | fitur baru |
| PATCH (`1.0.0` → `1.0.1`) | perbaikan bug |

`versionName` = tag, `versionCode` = `MAJOR*1_000_000 + MINOR*1_000 + PATCH` (selalu naik mengikuti SemVer).

## Cek update

Pengaturan → **Cek update** membaca `releases/latest` dari GitHub, membandingkan SemVer, lalu membuka APK-nya.
Update harus ditandatangani keystore yang sama agar bisa menimpa versi terpasang.

## Kredit & lisensi

- Lirik & format: [Better Lyrics](https://github.com/better-lyrics/better-lyrics) (GPL-3.0) dan [LRCLIB](https://lrclib.net).
- Proyek ini dilisensikan **GPL-3.0**. Tambahkan file `LICENSE` lewat GitHub (*Add file → Create new file → LICENSE → pilih GPL-3.0*).
- Hak cipta lirik dimiliki pemiliknya; Blyric hanya menampilkan lirik dari provider dan menyimpan cache lokal di perangkat pengguna.
