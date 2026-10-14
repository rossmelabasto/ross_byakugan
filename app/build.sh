#!/usr/bin/env bash
# Compila el APK sin Gradle: aapt2 → javac → d8 → zipalign → apksigner. Uso: ./build.sh [install]
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
AJ=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
OUT=build; KS=byakugan-debug.keystore
# JDK: el del PATH si trae javac; si no, el JBR de Android Studio.
if ! command -v javac >/dev/null; then export JAVA_HOME=/opt/android-studio/jbr; export PATH=$JAVA_HOME/bin:$PATH; fi
rm -rf $OUT && mkdir -p $OUT/{res,gen,obj,assets}

cp ../docs/TERMS.md $OUT/assets/terms.txt          # misma fuente que el servidor
"$BT/aapt2" compile --dir res -o $OUT/res
"$BT/aapt2" link -I "$AJ" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 36 \
    --version-code 1 --version-name 0.1.0 -A $OUT/assets --java $OUT/gen -o $OUT/base.apk $OUT/res/*.flat
javac --release 17 -Xlint:-options -cp "$AJ" -d $OUT/obj $(find src $OUT/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$AJ" --output $OUT $(find $OUT/obj -name '*.class')
cp $OUT/base.apk $OUT/unsigned.apk && (cd $OUT && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -f 4 $OUT/unsigned.apk $OUT/aligned.apk
[ -f $KS ] || keytool -genkeypair -keystore $KS -alias byakugan -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass byakugan -keypass byakugan -dname "CN=Byakugan" >/dev/null 2>&1
"$BT/apksigner" sign --ks $KS --ks-pass pass:byakugan --out $OUT/byakugan.apk $OUT/aligned.apk
ls -lh $OUT/byakugan.apk
[ "${1:-}" = install ] && adb install -r $OUT/byakugan.apk
