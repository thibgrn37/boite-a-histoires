# Boîte à histoires (code source)

Appli Android de podcasts pour un enfant qui ne lit pas encore : grille d'images, lecteur à 3 gros boutons,
téléchargement automatique en Wi‑Fi avec archive locale, espace parents protégé par code, mode kiosque (Device Owner).

Java pur, sans bibliothèque externe (API Android uniquement) : MediaPlayer + MediaSession, JobScheduler, XmlPullParser.

## Compiler

Outils : `aapt2`, `javac` (JDK 17+), `dx` (paquet Debian/Ubuntu `dalvik-exchange`), `zipalign`, `apksigner`,
un `android.jar` récent pour compiler (API 30+) et un `android.jar` pour l'édition de liens des ressources.

```bash
ANDROID_JAR_COMPILE=$ANDROID_HOME/platforms/android-35/android.jar \
ANDROID_JAR_LINK=$ANDROID_HOME/platforms/android-35/android.jar \
DX=dalvik-exchange ./build.sh
```

L'APK signé sort dans `build/BoiteAHistoires.apk`. La clé de signature est dans `keystore/` (mot de passe `boiteahistoires`) :
garde‑la, il faut la même clé pour installer une mise à jour sans perdre les données.

## Fichiers

- `MainActivity` : écrans enfant (podcasts → épisodes → lecteur) et code parent
- `ParentScreen` : espace parents
- `Store` : bibliothèque (JSON), réglages, positions de lecture
- `Sync`, `SyncJob`, `FeedParser`, `Net` : flux RSS, lien Apple → RSS, téléchargements
- `Player`, `PlayerService`, `VolumeCap` : lecture, saut d'intro, volume max
- `Kiosk`, `AdminReceiver`, `RescueReceiver` : mode kiosque et secours adb
