# PassVault

Gestionnaire de mots de passe Android (third-party), optimisé pour un Samsung Galaxy S22
(écran 1080×2340, capteur d'empreinte ultrasonique Goodix, One UI / Android 12+).

## Sécurité

- Chiffrement AES-256-GCM, clé générée et stockée dans le **Android Keystore** (non exportable).
- Verrouillage **biiométrique obligatoire** (`BiometricPrompt`, empreinte/visage) à chaque ouverture.
- Auto-verrouillage dès que l'app passe en arrière-plan.
- Presse-papiers effacé automatiquement après 30 secondes.
- `allowBackup=false` + règles d'extraction : aucune donnée incluse dans les sauvegardes cloud ou Smart Switch.
- Mots de passe masqués par défaut, révélation manuelle par entrée.
- Générateur de mots de passe aléatoires (`SecureRandom`, 20 caractères, sans caractères ambigus).

## Compilation

Prérequis : Android Studio (Koala ou plus récent), JDK 17, SDK Android 35.

```bash
# Debug
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

# Release (APK signé directement, pratique pour installation hors Play Store)
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

Ou simplement : ouvrir le dossier dans Android Studio → Build → Build APK(s).

## Installation sur le S22

1. Activer « Options développeur » (7 taps sur numéro de build) puis « Sources inconnues » / « Installer apps inconnues » pour votre navigateur.
2. Transférer l'APK et l'installer.
3. Enregistrer au moins une empreinte dans : Paramètres → Sécurité et confidentialité → Empreintes.
4. Ouvrir PassVault → authentification par empreinte (capteur sous l'écran du S22) → coffre déverrouillé.

## Compatibilité

- `compileSdk`/`targetSdk` 35 (Android 15), `minSdk` 31 (Android 12, version d'origine du S22).
- `BiometricManager.Authenticators.BIOMETRIC_STRONG` supporté par le capteur ultrasonique du S22.
