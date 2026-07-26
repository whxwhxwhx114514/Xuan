#!/bin/bash
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

echo "=== Cleaning ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/obj" "$BUILD_DIR/gen"

echo "=== Step1: aapt ==="
"$AAPT" package -f -m -J "$BUILD_DIR/gen" -M "$MANIFEST" -S "$RES_DIR" -I "$ANDROID_JAR" --auto-add-overlay

echo "=== Step2: javac ==="
JAVA_FILES=$(find "$SRC_DIR" -name "*.java" | tr '\n' ' ')
"$JAVAC" -d "$BUILD_DIR/obj" -classpath "$ANDROID_JAR" -sourcepath "$SRC_DIR:$BUILD_DIR/gen" -source 1.8 -target 1.8 $JAVA_FILES

echo "=== Step3: dx ==="
DX="/usr/lib/android-sdk/build-tools/debian/dx"
"$DX" --dex --output="$BUILD_DIR/classes.dex" "$BUILD_DIR/obj"

echo "=== Step4: package APK ==="
"$AAPT" package -f -M "$MANIFEST" -S "$RES_DIR" -I "$ANDROID_JAR" -F "$OUT_APK" --auto-add-overlay

echo "=== Step5: add dex ==="
cd "$BUILD_DIR"
rm -rf _apk_work
mkdir -p _apk_work
cd _apk_work
unzip -o "$OUT_APK" 2>&1 | tail -1
cd "$BUILD_DIR"
cp classes.dex _apk_work/
cd _apk_work
jar cf "$OUT_APK" . 2>&1 | tail -1 || zip -r "$OUT_APK" . 2>&1 | tail -1
cd "$BUILD_DIR"
rm -rf _apk_work

echo "=== Step6: zipalign ==="
"$ZIPALIGN" -f -p 4 "$OUT_APK" "$ALIGNED_APK"

echo "=== Step6.5: fix platformBuildVersionCode 23->36 ==="
cd "$BUILD_DIR"
rm -rf _fix
mkdir _fix
cd _fix
unzip -o "$ALIGNED_APK" > /dev/null 2>&1
python3 -c "
import struct
data = open('AndroidManifest.xml','rb').read()
idx = data.find(b'platformBuildVersionCode')
if idx > 0:
    chunk = data[idx:idx+80]
    for i in range(len(chunk)-4):
        if struct.unpack('<I', chunk[i:i+4])[0] == 23:
            new_data = data[:idx+i] + struct.pack('<I', 36) + data[idx+i+4:]
            open('AndroidManifest.xml','wb').write(new_data)
            print('Patched: 23 -> 36')
            break
    else:
        print('Value 23 not found')
else:
    print('platformBuildVersionCode not found')
" 2>&1
zip -r "$ALIGNED_APK" . > /dev/null 2>&1
cd "$BUILD_DIR"
rm -rf _fix

echo "=== Step7: sign ==="
KEYSTORE="$WORKSPACE/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v -keystore "$KEYSTORE" -alias xuan -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Xuan, OU=Dev, O=Xuan, L=City, S=State, C=CN" 2>/dev/null
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias xuan --key-pass pass:android --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true "$ALIGNED_APK" 2>/dev/null
cp "$ALIGNED_APK" "$FINAL_APK"

echo "=== Step8: copy ==="
cp "$FINAL_APK" "$DOWNLOAD_APK"

echo "=== BUILD SUCCESS ==="
ls -la "$FINAL_APK"