#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

if command -v gradle >/dev/null 2>&1; then
    echo "Compilation avec Gradle..."
    (cd "$ROOT" && gradle assembleDebug assembleRelease --no-daemon)
    cp "$ROOT/app/build/outputs/apk/release/app-release.apk" "$ROOT/GCU-Auto-Connexion-4.0.0.apk"
    cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/GCU-Auto-Connexion-4.0.0-debug.apk"
    echo "Succès ! APKs créés :"
    echo "  - $ROOT/GCU-Auto-Connexion-4.0.0.apk"
    echo "  - $ROOT/GCU-Auto-Connexion-4.0.0-debug.apk"
else
    echo "Gradle introuvable. Utilisez './gradlew assembleRelease' ou installez Gradle."
    exit 1
fi
