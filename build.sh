#!/bin/bash
# Compile l'APK sans Gradle : aapt2 + javac + dx + zipalign + apksigner.
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR_COMPILE="${ANDROID_JAR_COMPILE:-/opt/android-sdk/platforms/android-35/android.jar}"
ANDROID_JAR_LINK="${ANDROID_JAR_LINK:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX="${DX:-dalvik-exchange}"
OUT=build
rm -rf $OUT && mkdir -p $OUT/classes $OUT/gen
aapt2 compile --dir res -o $OUT/res.zip
aapt2 link -I "$ANDROID_JAR_LINK" --manifest AndroidManifest.xml -o $OUT/base.apk \
    --java $OUT/gen $OUT/res.zip --auto-add-overlay
python3 patch-manifest.py $OUT/base.apk
javac -nowarn -Xlint:none -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR_COMPILE" \
    -d $OUT/classes $(find src $OUT/gen -name '*.java')
$DX --dex --min-sdk-version=26 --output=$OUT/classes.dex $OUT/classes
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT && zip -q -j unsigned.apk classes.dex)
zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
apksigner sign --ks keystore/boite.keystore --ks-pass pass:boiteahistoires --ks-key-alias boite \
    --out $OUT/BoiteAHistoires.apk $OUT/aligned.apk
apksigner verify $OUT/BoiteAHistoires.apk
echo "OK : $OUT/BoiteAHistoires.apk"
