# PassVault

Gestionnaire de mots de passe Android (third-party), optimisé pour un Samsung Galaxy S22
(écran 1080×2340, capteur d'empreinte ultrasonique Goodix, One UI / Android 12+).

## Sécurité

- Chiffrement AES-256-GCM, clé générée et stockée dans le **Android Keystore** (non exportable).
- Verrouillage par **code PIN à 6 chiffres** obligatoire à chaque ouverture (haché en PBKDF2-SHA256, 120 000 itérations + sel aléatoire, stocké en préférences chiffrées).
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
4. Ouvrir PassVault → créer votre code PIN à 6 chiffres → coffre déverrouillé.

## Compatibilité

- `compileSdk`/`targetSdk` 35 (Android 15), `minSdk` 31 (Android 12, version d'origine du S22).
- Anti-brute-force : blocage 30 s après 5 codes incorrects.
