# 🛑 ReelGuard

Bloqueur de Reels pour Android — Instagram, YouTube Shorts, TikTok, Facebook, Snapchat et plus.

## 📱 Installation depuis GitHub

### Option A — Téléchargement direct (recommandée)

1. Allez dans l'onglet **[Releases](../../releases/latest)** de ce dépôt
2. Téléchargez le fichier `ReelGuard-vX.X.apk`
3. Sur votre téléphone Android, autorisez l'installation depuis votre navigateur ou gestionnaire de fichiers :
   - **Android 12+ (Pixel 9a)** : *Paramètres → Applications → Accès spécial → Installer des applis inconnues → [Votre navigateur] → Autoriser*
4. Ouvrez l'APK téléchargé et confirmez l'installation
5. Lancez **ReelGuard** et suivez le guide (2 permissions à accorder)

### Option B — Obtainium (mises à jour automatiques)

[Obtainium](https://github.com/ImranR98/Obtainium) est une app qui récupère et installe automatiquement les nouvelles versions depuis GitHub.

1. Installez Obtainium (lien dans leur README)
2. Ajoutez cette URL : `https://github.com/VOTRE_USERNAME/ReelGuard`
3. Obtainium vous notifiera à chaque nouvelle version

---

## ⚙️ Fonctionnalités

| Fonction | Description |
|---|---|
| 🎯 Détection des Reels | Instagram, YouTube Shorts, TikTok, Facebook, Snapchat, Pinterest, X |
| ⏱️ Quota par durée | Ex : 15 min de Reels par jour |
| 🔢 Quota par nombre | Ex : 10 Reels maximum par jour |
| 💬 Exception messagerie | Les vidéos dans les DMs ne sont pas bloquées |
| 🕐 Plages horaires | Bloquer automatiquement la nuit (ex: 22h–8h) |
| 🎯 Mode Focus | Blocage total pour 15/30/60/120 min |
| 🔒 PIN anti-contournement | Empêche la désactivation impulsive |
| 🔥 Streak | Compteur de jours où le quota est respecté |
| 📊 Statistiques | Graphique 7j, historique 30j |
| 📵 100% local | Aucune donnée envoyée à des serveurs |

---

## 🔧 Compiler soi-même

### Prérequis
- Android Studio Hedgehog 2023.1.1+
- JDK 17
- Android SDK 34

### Étapes
```bash
git clone https://github.com/VOTRE_USERNAME/ReelGuard.git
cd ReelGuard
./gradlew assembleDebug
# L'APK se trouve dans : app/build/outputs/apk/debug/
```

### Publier une nouvelle version
```bash
git tag v1.1
git push origin v1.1
# GitHub Actions build et publie l'APK automatiquement
```

---

## 🚀 Mise en place de GitHub Actions (une seule fois)

Pour que le build automatique fonctionne, vous devez configurer la signature de l'APK.

### 1. Générer un keystore
```bash
chmod +x scripts/generate_keystore.sh
./scripts/generate_keystore.sh
```
Le script affiche 4 valeurs à copier.

### 2. Ajouter les secrets dans GitHub
Dans votre dépôt : **Settings → Secrets and variables → Actions → New repository secret**

| Nom du secret | Valeur |
|---|---|
| `SIGNING_KEY` | La longue chaîne base64 affichée par le script |
| `KEY_ALIAS` | `reelguard-key` |
| `KEY_STORE_PASSWORD` | Le mot de passe affiché par le script |
| `KEY_PASSWORD` | Le mot de passe affiché par le script |

### 3. Publier
```bash
git tag v1.0
git push origin v1.0
```
→ GitHub Actions build l'APK, le signe, et crée une Release en ~5 minutes.

---

## 🏗️ Architecture

```
app/src/main/java/com/reelguard/app/
├── service/
│   ├── ReelBlockerAccessibilityService.kt  ← Détection des Reels
│   └── BootReceiver.kt
├── manager/
│   ├── QuotaManager.kt                     ← Logique des quotas
│   └── OverlayManager.kt                   ← Écran de blocage
├── data/                                   ← Room (SQLite local)
└── ui/                                     ← Jetpack Compose
    ├── dashboard/                          ← Écran principal
    ├── settings/                           ← Paramètres
    ├── stats/                              ← Statistiques
    └── onboarding/                         ← Guide premier lancement
```

---

## ⚠️ Note technique

La détection des Reels utilise l'**AccessibilityService** Android, qui inspecte l'arbre de vues des autres applications. Cette approche est stable mais peut nécessiter une mise à jour si Instagram, YouTube ou TikTok modifient significativement leur interface.

Toutes les données restent **exclusivement sur votre téléphone** (SharedPreferences + base Room locale).

---

## 📄 Licence

Usage personnel uniquement.
