# Blyric tidak memakai refleksi; aturan default R8 sudah cukup.

# Kuromoji memuat berkas kamus (.bin) sebagai resource RELATIF terhadap paket kelasnya,
# jadi nama paket/kelas tidak boleh diobfuskasi atau dipindah oleh R8.
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**
