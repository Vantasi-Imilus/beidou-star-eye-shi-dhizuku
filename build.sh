#!/usr/bin/env bash
# 北斗·群星之眼 —— 一键编译脚本（git-bash / Linux / macOS）
set -e
cd "$(dirname "$0")"

VERSION="0.3.0"
DIST="dist"

echo "=========================================="
echo "  北斗·群星之眼 一键编译"
echo "=========================================="

# 1. 定位 JDK：JAVA_HOME 优先；否则探测常见安装位置（不含任何本机路径）；最后回退 PATH
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  for j in \
    "/c/Program Files/Android/Android Studio/jbr" \
    "/c/Program Files/Java/jdk-21" \
    "/c/Program Files/Eclipse Adoptium/jdk-21.0.2-hotspot" \
    "$HOME/.jdks/temurin-21" \
    "$HOME/.sdkman/candidates/java/current" \
    "/usr/lib/jvm/java-21-openjdk-amd64" \
    "/usr/lib/jvm/java-21-openjdk" \
    "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"; do
    if [ -x "$j/bin/java" ]; then
      export JAVA_HOME="$j"
      break
    fi
  done
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  echo "[信息] 使用 JDK: $JAVA_HOME"
elif command -v java >/dev/null 2>&1; then
  echo "[提示] 未找到独立 JDK，使用 PATH 中的 java（要求 JDK 17+，推荐 21）"
  java -version 2>&1 | head -n 1
else
  echo "[错误] 未找到 JDK：请安装 JDK 21，或设置 JAVA_HOME 指向 JDK 安装目录。" >&2
  echo "       示例：export JAVA_HOME=/path/to/jdk-21" >&2
  exit 1
fi

# 2. 编译（debug 用于真机调试；release 复用 debug 签名、不开 R8，行为与 debug 等价）
echo "[信息] 编译 debug + release APK ..."
./gradlew :app:assembleDebug :app:assembleRelease --console=plain

# 3. 输出 APK
mkdir -p "$DIST"
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
  cp "app/build/outputs/apk/debug/app-debug.apk" "$DIST/BeidouStarEye-v${VERSION}-debug.apk"
  echo "[成功] APK(debug):   $DIST/BeidouStarEye-v${VERSION}-debug.apk"
fi
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
  cp "app/build/outputs/apk/release/app-release.apk" "$DIST/BeidouStarEye-v${VERSION}-release.apk"
  echo "[成功] APK(release): $DIST/BeidouStarEye-v${VERSION}-release.apk"
fi

# 4. 打包源码（排除 build/.gradle 产物）
echo "[信息] 打包源码 ..."
tar -czf "$DIST/BeidouStarEye-src-v${VERSION}.tar.gz" \
  --exclude='build' --exclude='.gradle' \
  app core feature gradle \
  build.gradle.kts settings.gradle.kts gradle.properties local.properties gradlew gradlew.bat
echo "[成功] 源码: $DIST/BeidouStarEye-src-v${VERSION}.tar.gz"

echo "=========================================="
echo "  完成"
echo "=========================================="
