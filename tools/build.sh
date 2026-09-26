#!/bin/sh
# Build a debug APK with the Debian Android tools (aapt, javac, d8-less dx via
# the platform jar, zipalign, apksigner). No Gradle and no Google SDK download.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build"
ANDROID_JAR="$ROOT/tools/android-35.jar"
KEYSTORE="$ROOT/tools/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/apk"

echo "== aapt2 (API 35) =="
AAPT2="/root/.gradle/caches/8.14.2/transforms/cf4a1c412ac545462a7f954d530dc829/transformed/aapt2-8.13.2-14304508-linux/aapt2"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/inkbench.unsigned.apk" --manifest "$SRC/AndroidManifest.xml" \
  -I "$ANDROID_JAR" --java "$OUT/gen" "$OUT/res.zip"

echo "== javac =="
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.list"
# android.jar is a stub jar: keep the real JDK bootstrap so javac can resolve
# java.lang, and put the Android stubs on the classpath. -source 8 keeps the
# bytecode inside what D8 and minSdk 21 accept.
javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.list"

echo "== dex =="
# Android 23's dx lives in the build-tools package when present. Fall back to
# the d8 jar shipped beside this script if the operator dropped one in.
if command -v dx >/dev/null 2>&1; then
  dx --dex --output="$OUT/classes.dex" "$OUT/classes"
elif [ -f "$ROOT/tools/d8.jar" ]; then
  find "$OUT/classes" -name '*.class' > "$OUT/class.list"
  # D8 8.2 rejects a directory argument ("unsupported source file type").
  tr '\n' '\0' < "$OUT/class.list" | xargs -0 java -cp "$ROOT/tools/d8.jar" com.android.tools.r8.D8 \
    --min-api 21 --lib "$ANDROID_JAR" --output "$OUT"
else
  echo "neither dx nor tools/d8.jar is available" >&2
  exit 1
fi

cd "$OUT"
jar uf inkbench.unsigned.apk classes.dex 2>/dev/null || \
  python3 - << 'PY'
import zipfile
apk="inkbench.unsigned.apk"
with zipfile.ZipFile(apk, "a") as z:
    z.write("classes.dex", "classes.dex")
PY

echo "== align + sign =="
zipalign -f 4 "$OUT/inkbench.unsigned.apk" "$OUT/inkbench.aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias inkbench -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Inkbench Debug,O=Inkbench,C=CN"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias inkbench --out "$ROOT/inkbench-debug.apk" "$OUT/inkbench.aligned.apk"
apksigner verify --verbose "$ROOT/inkbench-debug.apk"
echo "APK: $ROOT/inkbench-debug.apk"
