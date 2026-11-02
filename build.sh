#!/usr/bin/env bash
# Fast Store derleme betigi: Android SDK veya Gradle gerektirmez.
# Gerekenler: JDK 17+, Python 3, tools/ klasorundeki jar dosyalari.
set -euo pipefail
cd "$(dirname "$0")"

T=tools
OUT=out
VERSION=$(grep -o 'android:versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
APK=dist/FastStore-$VERSION.apk

[ -f "$T/android.jar" ] && [ -f "$T/dalvik-dx-16.0.1.jar" ] && [ -f "$T/apksig-2.3.0.jar" ] || "$T/indir.sh"
rm -rf "$OUT"
mkdir -p "$OUT/classes" dist keystore

echo "1/5 Kaynaklar derleniyor"
python3 build/mini_aapt.py AndroidManifest.xml res "$OUT/res" com.kisisel.magaza

echo "2/5 Java derleniyor"
find src "$OUT/res/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
      -bootclasspath "$T/android.jar" -d "$OUT/classes" @"$OUT/sources.txt"

echo "3/5 DEX oluşturuluyor"
java -cp "$T/dalvik-dx-16.0.1.jar" com.android.dx.command.Main \
     --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"

echo "4/5 APK paketleniyor"
python3 build/package.py "$OUT/unsigned.apk" "$OUT/res" "$OUT/classes.dex"

echo "5/5 İmzalanıyor"
KS=keystore/magazam.p12
if [ ! -f "$KS" ]; then
  if [ -n "${CI:-}" ]; then echo "HATA: CI ortamında imza anahtarı bulunamadı" >&2; exit 1; fi
  PASS=$(python3 -c "import secrets;print(secrets.token_urlsafe(18))")
  keytool -genkeypair -storetype PKCS12 -keystore "$KS" -storepass "$PASS" \
          -alias magazam -keyalg RSA -keysize 4096 -validity 10000 \
          -dname "CN=Magazam, O=Kisisel, C=TR" >/dev/null 2>&1
  echo "$PASS" > keystore/sifre.txt
  chmod 600 "$KS" keystore/sifre.txt
  echo "   Yeni imza anahtarı oluşturuldu: $KS"
fi
PASS=$(cat keystore/sifre.txt)
mkdir -p "$OUT/signer"
JOPTS="--add-exports=java.base/sun.security.x509=ALL-UNNAMED --add-exports=java.base/sun.security.pkcs=ALL-UNNAMED --add-exports=java.base/sun.security.util=ALL-UNNAMED"
javac -nowarn -cp "$T/apksig-2.3.0.jar" -d "$OUT/signer" build/Signer.java
java $JOPTS -cp "$T/apksig-2.3.0.jar:$OUT/signer" Signer sign "$KS" "$PASS" magazam "$OUT/unsigned.apk" "$APK"
java $JOPTS -cp "$T/apksig-2.3.0.jar:$OUT/signer" Signer verify "$APK"
echo "Hazır: $APK ($(du -h "$APK" | cut -f1))"
