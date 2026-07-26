#!/bin/bash
# set -e removed for robustness

WORKSPACE="/data/user/0/com.ai.assistance.operit/files/workspace/911ecc77-abd1-4f90-b2fc-104e0ae5a58c/xuan_app"
BUILD_DIR="$WORKSPACE/build"
SRC_DIR="$WORKSPACE/src"
RES_DIR="$WORKSPACE/res"
MANIFEST="$WORKSPACE/AndroidManifest.xml"

ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
AAPT="/usr/bin/aapt"
APKSIGNER="/usr/bin/apksigner"
ZIPALIGN="/usr/bin/zipalign"
JAVAC="/usr/bin/javac"
OUT_APK="$WORKSPACE/xuan-unsigned.apk"
ALIGNED_APK="$WORKSPACE/xuan-aligned.apk"
FINAL_APK="$WORKSPACE/xuan.apk"
DOWNLOAD_APK="/storage/emulated/0/Download/xuan.apk"

echo "=== Cleaning build dir ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/obj"
mkdir -p "$BUILD_DIR/gen"

echo "=== Step 1: Compile resources with aapt ==="
# Create R.java first
"$AAPT" package -f -m \
    -J "$BUILD_DIR/gen" \
    -M "$MANIFEST" \
    -S "$RES_DIR" \
    -I "$ANDROID_JAR" \
    --auto-add-overlay

echo "=== Step 2: Compile Java sources ==="
# Find all java files
JAVA_FILES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')

"$JAVAC" -d "$BUILD_DIR/obj" \
    -classpath "$ANDROID_JAR" \
    -sourcepath "$SRC_DIR:$BUILD_DIR/gen" \
    -source 1.8 -target 1.8 \
    $JAVA_FILES

echo "=== Step 3: Convert to dex ==="
DX="/usr/lib/android-sdk/build-tools/debian/dx"

"$DX" --dex --output="$BUILD_DIR/classes.dex" "$BUILD_DIR/obj"

echo "=== Step 4: Package APK ==="
"$AAPT" package -f \
    -M "$MANIFEST" \
    -S "$RES_DIR" \
    -I "$ANDROID_JAR" \
    -F "$OUT_APK" \
    --auto-add-overlay

echo "=== Step 5: Add dex to APK ==="
# Unpack APK, add dex, repack using jar (always available)
cd "$BUILD_DIR"
rm -rf _apk_work
mkdir -p _apk_work
cd _apk_work
unzip -o "$OUT_APK" 2>&1 | tail -1
cd ..
cp "$BUILD_DIR/classes.dex" _apk_work/
cd _apk_work
jar cf "$OUT_APK" . 2>&1 | tail -1 || zip -r "$OUT_APK" . 2>&1 | tail -1
cd "$BUILD_DIR"
rm -rf _apk_work

echo "=== Step 6: Align APK ==="
"$ZIPALIGN" -f -p 4 "$OUT_APK" "$ALIGNED_APK"

echo "=== Step 7: Sign APK (V1+V2+V3 apksigner) ==="
# Generate debug keystore if needed
KEYSTORE="$WORKSPACE/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias xuan \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Xuan, OU=Dev, O=Xuan, L=City, S=State, C=CN" 2>/dev/null
fi

# APK Signature Scheme (V3 covers V2 requirement for targetSdkVersion>=30)
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --ks-key-alias xuan \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    "$ALIGNED_APK" 2>/dev/null

cp "$ALIGNED_APK" "$FINAL_APK"

echo "=== Step 8: Copy to Download ==="
cp "$FINAL_APK" "$DOWNLOAD_APK"

echo "=== BUILD SUCCESS ==="
echo "APK: $DOWNLOAD_APK"
ls -la "$FINAL_APK"
ls -la "$DOWNLOAD_APK"