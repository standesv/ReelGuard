#!/bin/bash
# Commit de TOUT le travail en attente + (re)création propre du tag v3.46 + push.
# A lancer depuis Git Bash, à la racine du projet :  bash scripts/release.sh
set -e
cd "$(dirname "$0")/.."

echo "== 1/6 Nettoyage d'un eventuel verrou git =="
rm -f .git/index.lock

echo "== 2/6 Ajout de tous les changements =="
git add -A

echo "== 3/6 Commit =="
git commit -m "chore: ship pending work - Play readiness, API 36, messaging fix (v3.46)" \
  || echo "(rien a committer - deja fait, on continue)"

echo "== 4/6 Suppression des tags mal places v3.45 / v3.46 (local + distant) =="
git tag -d v3.45 2>/dev/null || true
git tag -d v3.46 2>/dev/null || true
git push origin :refs/tags/v3.45 2>/dev/null || true
git push origin :refs/tags/v3.46 2>/dev/null || true

echo "== 5/6 Push du code =="
git push origin main

echo "== 6/6 Nouveau tag v3.46 sur le commit a jour + push (declenche le build) =="
git tag v3.46
git push origin v3.46

echo ""
echo "================ VERIFICATION ================"
git show v3.46:app/build.gradle.kts | grep -E "versionCode|versionName|targetSdk|compileSdk"
echo "============================================="
echo "Si tu vois versionCode = 46 et targetSdk = 36 ci-dessus : c'est BON."
echo "Va ensuite dans l'onglet Actions de GitHub, attends le build vert,"
echo "puis telecharge ReelGuard-v3.46.aab (versionCode 46) et uploade-le."
