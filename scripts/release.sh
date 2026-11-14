#!/usr/bin/env bash
# Pemakaian: scripts/release.sh 0.2.0
# Membuat tag v0.2.0 lalu push -> GitHub Actions membangun APK signed & membuat Release.
set -euo pipefail

v="${1:?pemakaian: scripts/release.sh MAJOR.MINOR.PATCH}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] || { echo "'$v' bukan SemVer"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Ada perubahan yang belum di-commit"; exit 1; }

git tag -a "v$v" -m "Blyric v$v"
git push origin "v$v"
echo "Tag v$v terkirim. Pantau di tab Actions."
