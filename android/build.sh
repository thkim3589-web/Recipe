#!/bin/bash
# 레시피 찾기 APK 빌드 스크립트
# 사용법: KS=서명키.jks KS_PASS=비밀번호 ./android/build.sh
# 새 버전을 낼 때는 AndroidManifest.xml의 versionCode를 올리고, apk/version.json도 같은 숫자로 맞추세요.
set -e
SDK=${ANDROID_HOME:-$HOME/android-sdk}
BT=$SDK/build-tools/35.0.0
AJ=$SDK/platforms/android-34/android.jar
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes build/dex assets
cp ../recipe-finder.html assets/index.html
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $AJ --manifest AndroidManifest.xml -A assets build/res.zip --java build/gen
javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath $AJ -d build/classes build/gen/com/thkim/recipe/R.java src/com/thkim/recipe/MainActivity.java
(cd build/classes && jar cf ../classes.jar .)
$BT/d8 --release --min-api 24 --lib $AJ --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk && (cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --out ../apk/recipe.apk build/aligned.apk
rm -rf build assets
echo "완료: apk/recipe.apk"
