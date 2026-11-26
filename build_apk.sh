#!/bin/bash
# 自律OS 本地构建脚本（适配当前环境）
# 依赖：JDK 17+ / Android SDK（build-tools 33.0.2 与 platforms/android-33）
set -e

# 注意：D8（build-tools 33.0.2）与 JDK 25 不兼容，构建须用 JDK 17。
# 如需换 JDK，请修改下面这一行。
export JAVA_HOME="/root/.local/share/mise/installs/java/17"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
BT="$ANDROID_HOME/build-tools/33.0.2"
AJ="$ANDROID_HOME/platforms/android-33/android.jar"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR"
APP="$ROOT/安卓工程"
BUILD="$ROOT/.build"
OUT="${OUT:-$ROOT/自律OS-v1.3.apk}"
KS="$APP/签名/selfdrive.jks"

VER_CODE="${VER_CODE:-8}"
VER_NAME="${VER_NAME:-1.3.4}"
MIN_SDK=21
# targetSdk 保持 32：通知权限在安装时自动授予，小米/安卓 13 上最稳。
TGT_SDK=32

echo "==> 使用 JAVA_HOME=$JAVA_HOME"
echo "==> 使用 ANDROID_HOME=$ANDROID_HOME"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex"

# 0. 主 HTML 同步进 assets
cp "$ROOT/自律OS.html" "$APP/assets/index.html"
echo "[0] 已同步 自律OS.html -> assets/index.html"

# 1. 编译 Java
JAVA_SRC="$(find "$APP/src" -name '*.java' | tr '\n' ' ')"
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -classpath "$AJ" \
  -d "$BUILD/classes" $JAVA_SRC 2>&1 | grep -v "warning:" | grep -v "^$" || true
echo "[1] classes: $(find "$BUILD/classes" -name '*.class' | wc -l)"

# 2. dex
"$JAVA_HOME/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJ" --min-api "$MIN_SDK" --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class') 2>&1 | tail -3
echo "[2] dex: $(stat -c%s "$BUILD/dex/classes.dex") bytes"

# 3. 资源
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"
"$BT/aapt2" link -o "$BUILD/base.apk" -I "$AJ" \
  --manifest "$APP/AndroidManifest.xml" -A "$APP/assets" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TGT_SDK" \
  --version-code "$VER_CODE" --version-name "$VER_NAME" "$BUILD/res.zip"

# 4. 追加 classes.dex
python3 - "$BUILD/base.apk" "$BUILD/dex/classes.dex" <<'PY'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w") as zout:
    for it in zin.infolist():
        data = zin.read(it.filename)
        zi = zipfile.ZipInfo(it.filename, date_time=(1980,1,1,0,0,0))
        zi.external_attr = it.external_attr
        zi.compress_type = it.compress_type
        zout.writestr(zi, data)
    zi = zipfile.ZipInfo("classes.dex", date_time=(1980,1,1,0,0,0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    zout.writestr(zi, open(dex,"rb").read())
shutil.move(tmp, apk)
PY
echo "[4] entries: $(python3 -c "import zipfile;print([n for n in zipfile.ZipFile('$BUILD/base.apk').namelist()])")"

# 5. 对齐 + 签名（复用同一把密钥，保证可覆盖安装）
"$BT/zipalign" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:selfdrive2026 \
  --key-pass pass:selfdrive2026 --ks-key-alias selfdrive \
  --v1-signing-enabled true --v2-signing-enabled true --out "$OUT" "$BUILD/aligned.apk"

# 6. 校验
"$BT/apksigner" verify --min-sdk-version "$MIN_SDK" "$OUT" && echo "[6] signature OK"
"$BT/aapt2" dump badging "$OUT" | grep -E "^package:|launchable-activity|uses-permission"
ls -lh "$OUT"
