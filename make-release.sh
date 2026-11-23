#!/bin/bash
# Prépare une nouvelle version : augmente versionCode/versionName, compile, et crée release/
# (BoiteAHistoires.apk + version.json) à publier comme « release » GitHub.
# Usage : ./make-release.sh 1.1
set -euo pipefail
cd "$(dirname "$0")"
NAME="${1:?donne un numéro de version, ex. 1.1}"
CODE=$(( $(grep -o 'android:versionCode="[0-9]*"' AndroidManifest.xml | grep -o '[0-9]*') + 1 ))
sed -i.bak -E "s/android:versionCode=\"[0-9]+\"/android:versionCode=\"$CODE\"/; s/android:versionName=\"[^\"]*\"/android:versionName=\"$NAME\"/" AndroidManifest.xml && rm -f AndroidManifest.xml.bak
./build.sh
mkdir -p release
cp build/BoiteAHistoires.apk release/
printf '{"versionCode": %s, "versionName": "%s", "apk": "BoiteAHistoires.apk"}\n' "$CODE" "$NAME" > release/version.json
echo "Version $NAME ($CODE) prête dans release/ :"
echo "  gh release create v$NAME release/BoiteAHistoires.apk release/version.json --title \"$NAME\""
