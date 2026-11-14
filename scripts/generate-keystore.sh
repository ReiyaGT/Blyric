#!/usr/bin/env bash
# Membuat keystore rilis Blyric. Jalankan SEKALI, simpan file .jks + password di tempat aman (password manager + backup).
# Kalau keystore hilang, update tidak bisa dipasang di atas versi yang sudah terpasang.
set -euo pipefail

OUT="${1:-blyric-release.jks}"
ALIAS="${2:-blyric}"

[ -e "$OUT" ] && { echo "$OUT sudah ada, tidak ditimpa."; exit 1; }

keytool -genkeypair -v \
  -keystore "$OUT" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Blyric, O=Blyric"

echo
echo "== Isi GitHub Secrets (Settings > Secrets and variables > Actions) =="
echo "KEYSTORE_BASE64   : hasil perintah di bawah"
echo "KEYSTORE_PASSWORD : password keystore tadi"
echo "KEY_ALIAS         : $ALIAS"
echo "KEY_PASSWORD      : password key tadi"
echo
echo "Base64 keystore:"
if base64 --help 2>&1 | grep -q -- "-w"; then base64 -w0 "$OUT"; else base64 -i "$OUT"; fi
echo
