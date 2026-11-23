# Boîte à histoires : installation sur le Redmi 13C

Il faut compter environ 20 minutes, une seule fois. Il te faut le Mac, un câble USB-C et le téléphone (réinitialisé, sans compte Google ni Xiaomi : c'est déjà le cas).

## 1. Activer le débogage USB sur le téléphone

1. **Paramètres → À propos du téléphone** : tape 7 fois sur **« Version de HyperOS »** (ou « Version MIUI ») jusqu'au message « Vous êtes développeur ».
2. **Paramètres → Paramètres supplémentaires → Options pour les développeurs** : active **« Débogage USB »**.

Les options « Installer via USB » et « Débogage USB (paramètres de sécurité) » demandent un compte Xiaomi. On n'en a pas besoin au départ (voir l'étape 4 si besoin).

## 2. Installer adb sur le Mac

Dans le Terminal :

```bash
cd ~/Downloads
curl -O https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
unzip -o platform-tools-latest-darwin.zip
cd platform-tools
./adb devices
```

Branche le téléphone. Sur le téléphone, accepte **« Autoriser le débogage USB »** (coche « Toujours autoriser »). `./adb devices` doit afficher une ligne qui se termine par `device`.

(Avec Homebrew, c'est aussi possible : `brew install --cask android-platform-tools`, puis utilise `adb` au lieu de `./adb`.)

## 3. Installer l'appli

Toujours dans `~/Downloads/platform-tools` :

```bash
./adb install ~/Downloads/BoiteAHistoires.apk
```

**Si Xiaomi refuse** (`INSTALL_FAILED_USER_RESTRICTED`) : copie le fichier sur le téléphone et installe-le à la main.

```bash
./adb push ~/Downloads/BoiteAHistoires.apk /sdcard/Download/
```

Puis sur le téléphone : **Gestionnaire de fichiers → Téléchargements → BoiteAHistoires.apk → Installer** (autorise « cette source » si c'est demandé).

### Réglages batterie (à faire maintenant, avant le mode kiosque)

Sur le téléphone, va dans **Paramètres → Applications → Gérer les applications → Boîte à histoires** :
- **Économiseur de batterie → « Aucune restriction »**
- **Démarrage automatique → activé**

Et depuis le Mac (facultatif, ça renforce le réglage) :

```bash
./adb shell dumpsys deviceidle whitelist +fr.boiteahistoires.app
```

## 4. Activer le mode kiosque (une seule fois)

```bash
./adb shell dpm set-device-owner fr.boiteahistoires.app/.AdminReceiver
```

Réponse attendue : `Success: Device owner set to package ...`

Ouvre ensuite l'appli : le téléphone est maintenant bloqué dessus. Il démarre directement dans l'appli, sans écran de verrouillage, sans barre d'état ni bouton pour en sortir.

**Si ça échoue :**
- *« already some accounts on the device »* : va dans **Paramètres → Comptes** et supprime tous les comptes, puis relance la commande.
- *« already several users »* : supprime le « Second espace » ou l'utilisateur invité, puis relance.
- *« SecurityException: Calling identity is not authorized »*, *« … MANAGE_DEVICE_ADMINS »* ou *« Neither user 2000 … »* : c'est une particularité de Xiaomi, il faut l'option « Débogage USB (paramètres de sécurité) ». Voici comment faire :
  1. Connecte temporairement un compte Xiaomi (Paramètres → Compte Xiaomi). Une carte SIM peut être demandée.
  2. Dans les Options pour les développeurs, active **« Débogage USB (paramètres de sécurité) »**.
  3. Déconnecte le compte Xiaomi (Paramètres → Compte Xiaomi → Se déconnecter), puis vérifie dans Paramètres → Comptes qu'il n'en reste aucun.
  4. Relance la commande `dpm set-device-owner`.

## 5. Utilisation

- **Enfant** : il touche l'image d'un podcast, puis l'image d'un épisode, et ça joue. Il y a 3 gros boutons : précédent, lecture/pause, suivant. La flèche orange sert à revenir en arrière. Un ✔ vert marque les épisodes déjà écoutés.
- **Espace parents** : fais un **appui long de 3 secondes en haut à droite** de l'écran d'accueil, puis tape le code **1234**. Pense à le changer.
  - **Ajouter un podcast** : donne un nom et un lien (flux RSS ou lien Apple Podcasts). Avec juste le nom, l'appli le cherche sur Apple Podcasts. Le téléchargement démarre tout de suite.
  - **Passer le début** : c'est le nombre de secondes de jingle à sauter, réglable pour chaque podcast (Les Bestioles : 6 s au départ).
  - **Épisodes gardés** : c'est le nombre d'épisodes les plus récents gardés sur le téléphone (30 par défaut). Ceux qui disparaissent du flux sont conservés quand même.
  - Tu y trouves aussi le **volume maximum**, le bouton **« Mettre à jour maintenant »** et **« Quitter le mode kiosque »**.
- Les épisodes se téléchargent tout seuls toutes les 6 heures quand le téléphone est en Wi-Fi. Ensuite, tout marche hors ligne (dans la voiture par exemple).
- Pour **éteindre** : appui long sur le bouton marche/arrêt.

## 6. Secours : code oublié

Branche le téléphone au Mac, puis :

```bash
./adb shell am broadcast -a fr.boiteahistoires.app.SECOURS -n fr.boiteahistoires.app/.RescueReceiver
```

Le code redevient 1234 et le mode kiosque est désactivé.

## 7. Mises à jour de l'appli (automatiques)

Plus besoin du Mac : deux fois par jour en Wi-Fi, l'appli lit le fichier `version.json` de la dernière « release » du dépôt GitHub `thibgrn37/boite-a-histoires`. Si une version plus récente existe, elle la télécharge et l'installe toute seule, sans rien demander (grâce au mode « propriétaire de l'appareil »), puis se rouvre. Tu as aussi un bouton **« Vérifier les mises à jour »** dans l'espace parents.

Les nouvelles versions doivent être signées avec la **même clé** (`keystore/boite.keystore`, gardée à part, jamais publiée), sinon Android refuse l'installation.

## 8. Désinstaller l'appli

- **Désinstaller** : dans l'espace parents, touche « Retirer les droits d'administration », puis désinstalle normalement.
