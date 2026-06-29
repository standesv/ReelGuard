# ReelGuard — Guide d'installation

## Prérequis
- Android Studio Hedgehog (2023.1.1) ou plus récent
- Android SDK 34
- JDK 17

## Étapes

1. **Ouvrir le projet**
   - Lancez Android Studio
   - File > Open > sélectionnez le dossier `ReelGuard`
   - Attendez la synchronisation Gradle (~2 min au premier lancement)

2. **Compiler et installer**
   - Connectez votre Pixel 9a en USB (activez le débogage USB dans les options développeur)
   - Cliquez sur ▶ Run ou `Shift+F10`

3. **Permissions requises** (guide apparaît au premier lancement)
   - **Accessibilité** : Paramètres > Accessibilité > Applications installées > ReelGuard → Activer
   - **Affichage par-dessus** : Paramètres > Applications > ReelGuard > Afficher par-dessus d'autres apps → Activer

4. **Configurer vos quotas**
   - Ouvrez ReelGuard > Paramètres
   - Activez "Quota par durée" et définissez 15 min/jour (recommandé pour commencer)

## Architecture du projet

```
app/src/main/java/com/reelguard/app/
├── MainActivity.kt                    — Point d'entrée
├── ReelGuardApp.kt                    — Application class
├── service/
│   ├── ReelBlockerAccessibilityService.kt  — Détection des Reels
│   └── BootReceiver.kt                — Reset au redémarrage
├── manager/
│   ├── QuotaManager.kt               — Logique des quotas
│   └── OverlayManager.kt             — Écran de blocage
├── data/
│   ├── AppDatabase.kt                — Base Room
│   ├── entity/                       — Entités BDD
│   └── dao/                          — Accès données
└── ui/
    ├── dashboard/                    — Écran principal
    ├── settings/                     — Paramètres
    ├── stats/                        — Statistiques
    ├── onboarding/                   — Premier lancement
    ├── navigation/                   — Navigation Compose
    └── theme/                        — Thème Material3
```

## Applications ciblées
- Instagram (Reels)
- YouTube (Shorts)
- TikTok
- Facebook (Reels)
- Snapchat (Spotlight)
- Pinterest (Idea Pins)
- X / Twitter (vidéos)

## Notes techniques
- La détection repose sur l'AccessibilityService + inspection de l'arbre de vues
- Les patterns de détection peuvent nécessiter une mise à jour si les apps changent leurs UI internes
- Toutes les données restent localement sur le téléphone (Room + SharedPreferences)
