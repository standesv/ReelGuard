#!/bin/bash
# Commit de tout le travail en attente + (re)creation propre du tag de version + push.
# Le numero de version est lu automatiquement dans app/build.gradle.kts.
# A lancer depuis Git Bash :  bash scripts/release.sh
set -e
cd "$(dirname "$0")/.."

VERSION=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([0-9.]+)".*/\1/')
TAG="v${VERSION}"
echo "== Version detectee : ${VERSION}  -> tag ${TAG} =="

echo "== 1/6 Nettoyage d'un eventuel verrou git =="
rm -f .git/index.lock

echo "== 2/6 Ajout de tous les changements =="
git add -A

echo "== 3/6 Commit =="
git commit -m "release ${TAG}" || echo "(rien a committer - deja fait, on continue)"

echo "== 4/6 Suppression du tag ${TAG} s'il existait deja (local + distant) =="
git tag -d "${TAG}" 2>/dev/null || true
git push origin ":refs/tags/${TAG}" 2>/dev/null || true

echo "== 5/6 Push du code =="
git push origin main

echo "== 6/6 Creation du tag ${TAG} + push (declenche le build) =="
git tag "${TAG}"
git push origin "${TAG}"

echo ""
echo "================ VERIFICATION ================"
git show "${TAG}:app/build.gradle.kts" | grep -E "versionCode|versionName|targetSdk|compileSdk"
echo "============================================="
echo "Attendu : versionCode = 47, versionName = 3.47, targetSdk = 36."
echo "Va dans l'onglet Actions, attends le build VERT, telecharge ReelGuard-${TAG}.aab et uploade-le."
