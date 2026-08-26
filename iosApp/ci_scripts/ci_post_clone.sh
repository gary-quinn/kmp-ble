#!/bin/sh
# ci_post_clone.sh - Xcode Cloud custom build script (Post-Clone / Pre-Build).
#
# Purpose (Option B / "decouple Kotlin from Xcode"): build the Kotlin
# Multiplatform framework (KmpBleSample) BEFORE xcodebuild archives the iOS app.
# This avoids the native "Compile Kotlin Framework" build phase calling Gradle
# on Xcode Cloud, where xcodebuild runs with `-hideShellScriptEnvironment`
# (strips JAVA_HOME/PATH and has no JDK) and fails with "Unable to locate a
# Java Runtime" - the exact failure in the attached build log.
#
# Xcode Cloud runs this after cloning. It:
#   1. Locates or downloads a JDK 21 (Eclipse Temurin).
#   2. Builds the release device framework:
#          ./gradlew :sample:linkReleaseFrameworkIosArm64
#      (output: sample/build/bin/iosArm64/releaseFramework/KmpBleSample.framework)
#   3. Copies it to the exact path Xcode resolves at archive time:
#          sample/build/xcode-frameworks/Release/iphoneos26.5/KmpBleSample.framework
#      (Xcode's FRAMEWORK_SEARCH_PATHS = .../xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME);
#       on Xcode 26+ SDK_NAME is iphoneos<version>, not plain iphoneos)
#   4. Writes a marker file; the "Compile Kotlin Framework" build phase checks
#      it and skips its own Gradle invocation.
#
# Location: this script lives at iosApp/ci_scripts/ (same directory level as
# iosApp.xcodeproj). Xcode Cloud only discovers ci_scripts next to the project,
# not at the repository root.
set -e

# ---------------------------------------------------------------------------
# 0) Locate paths
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"         # repo root (two levels up from iosApp/ci_scripts)
GRADLEW="$REPO_DIR/gradlew"
FRAMEWORK_SRC="$REPO_DIR/sample/build/bin/iosArm64/releaseFramework/KmpBleSample.framework"
FRAMEWORKS_DIR="$REPO_DIR/sample/build/xcode-frameworks"
MARKER="$FRAMEWORKS_DIR/.xcode-cloud-prebuild"

echo "[ci_post_clone] Stage: Post-Clone activated"
echo "[ci_post_clone] Repository path: $REPO_DIR"
echo "[ci_post_clone] CI action: ${CI_XCODEBUILD_ACTION:-unknown}"

# ---------------------------------------------------------------------------
# 1) Resolve a working Java runtime (JDK 21)
# ---------------------------------------------------------------------------
java_works() {
  [ -x "$1" ] || return 1
  "$1" -version >/dev/null 2>&1 || return 1
  JDK_HOME="$(cd "$(dirname "$1")/.." && pwd)"
  [ -f "$JDK_HOME/conf/security/java.security" ]
}

JAVA_CMD=""

# 1a) Existing JAVA_HOME
if [ -n "$JAVA_HOME" ] && java_works "$JAVA_HOME/bin/java"; then
  JAVA_CMD="$JAVA_HOME/bin/java"
  echo "[ci_post_clone] Using JAVA_HOME: $JAVA_HOME"
fi

# 1b) A java already on PATH
if [ -z "$JAVA_CMD" ] && command -v java >/dev/null 2>&1 && java_works "$(command -v java)"; then
  JAVA_CMD="$(command -v java)"
  echo "[ci_post_clone] Using java on PATH: $JAVA_CMD"
fi

# 1c) A JDK already installed under /Library or ~/Library
if [ -z "$JAVA_CMD" ] && [ -x /usr/libexec/java_home ]; then
  JH="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
  if [ -n "$JH" ] && java_works "$JH/bin/java"; then
    JAVA_CMD="$JH/bin/java"
    echo "[ci_post_clone] Using java_home JDK: $JH"
  fi
fi

# 1d) Download a portable Temurin 21 JDK into the workspace. Xcode Cloud has
#     no JDK and no brew, so we fetch one from the Adoptium CDN.
if [ -z "$JAVA_CMD" ]; then
  case "$(uname -m)" in
    arm64|aarch64) ARCH="aarch64" ;;
    *)             ARCH="x64"     ;;
  esac
  # Adoptium uses "mac" for macOS (not "darwin" from `uname`).
  case "$(uname | tr 'A-Z' 'a-z')" in
    darwin) OS="mac" ;;
    *)      OS="$(uname | tr 'A-Z' 'a-z')" ;;
  esac
  JAVA_URL="https://api.adoptium.net/v3/binary/latest/21/ga/${OS}/${ARCH}/jdk/hotspot/normal/eclipse"
  JAVA_CACHE="$SCRIPT_DIR/.jdk"

  echo "[ci_post_clone] Downloading Temurin JDK 21 (${OS}/${ARCH}) from Adoptium..."
  rm -rf "$JAVA_CACHE"
  mkdir -p "$JAVA_CACHE"
  TARBALL="$JAVA_CACHE/jdk.tar.gz"
  curl -fL --retry 3 -o "$TARBALL" "$JAVA_URL"
  tar xzf "$TARBALL" -C "$JAVA_CACHE"
  rm -f "$TARBALL"

  # macOS Temurin ships as a .jdk bundle (Contents/Home/{bin,lib,conf,...}).
  # Use that layout as JAVA_HOME; do not flatten dirs (Gradle needs conf/security).
  JAVA_BIN="$(find "$JAVA_CACHE" -type f -path '*/Contents/Home/bin/java' 2>/dev/null | head -1)"
  if [ -z "$JAVA_BIN" ]; then
    JAVA_BIN="$(find "$JAVA_CACHE" -type f -path '*/bin/java' 2>/dev/null | head -1)"
  fi
  if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN" ]; then
    JAVA_CMD="$JAVA_BIN"
    JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    echo "[ci_post_clone] Installed Temurin JDK 21 at: $JAVA_HOME"
  fi
fi

if [ -z "$JAVA_CMD" ]; then
  echo "[ci_post_clone] error: Unable to obtain a working Java runtime (JDK 21)." >&2
  exit 1
fi

if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME="$(cd "$(dirname "$JAVA_CMD")/.." && pwd)"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "[ci_post_clone] Using Java: $JAVA_CMD"

# ---------------------------------------------------------------------------
# 2) Build the release device framework
# ---------------------------------------------------------------------------
# linkReleaseFrameworkIosArm64 (Compose + multi-module sample) needs far more than
# the repo-default 2g Gradle heap. Override only for Xcode Cloud; keep gradle.properties
# unchanged for local dev machines that may have less RAM.
GRADLE_CI_JVMARGS="-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"

echo "[ci_post_clone] Building release iosArm64 framework (linkReleaseFrameworkIosArm64)..."
echo "[ci_post_clone] Gradle JVM args (CI): $GRADLE_CI_JVMARGS"
(
  cd "$REPO_DIR"
  GRADLE_OPTS="-Dorg.gradle.jvmargs=$GRADLE_CI_JVMARGS" \
    "$GRADLEW" :sample:linkReleaseFrameworkIosArm64 \
      --console=plain \
      --no-daemon \
      --max-workers=2 \
      -Dorg.gradle.jvmargs="$GRADLE_CI_JVMARGS"
)

if [ ! -d "$FRAMEWORK_SRC" ]; then
  echo "[ci_post_clone] error: Build succeeded but framework not found at $FRAMEWORK_SRC" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 3) Place it where Xcode resolves at archive time (Release + SDK_NAME)
# ---------------------------------------------------------------------------
# FRAMEWORK_SEARCH_PATHS uses $(SDK_NAME), e.g. iphoneos26.5 on Xcode 26.
SDK_VERSION="$(xcrun --sdk iphoneos --show-sdk-version 2>/dev/null || true)"
if [ -n "$SDK_VERSION" ]; then
  SDK_NAME="iphoneos${SDK_VERSION}"
else
  SDK_NAME="iphoneos"
fi
FRAMEWORK_DEST="$FRAMEWORKS_DIR/Release/$SDK_NAME"

echo "[ci_post_clone] Xcode SDK_NAME: $SDK_NAME"
echo "[ci_post_clone] Copying framework to Xcode search path: $FRAMEWORK_DEST"
mkdir -p "$FRAMEWORK_DEST"
rm -rf "$FRAMEWORK_DEST/KmpBleSample.framework"
cp -R "$FRAMEWORK_SRC" "$FRAMEWORK_DEST/"

# ---------------------------------------------------------------------------
# 4) Mark the framework as pre-built so the Xcode "Compile Kotlin Framework"
#    build phase skips its own (Java-less) Gradle invocation.
# ---------------------------------------------------------------------------
date -u +%s > "$MARKER"
echo "[ci_post_clone] Framework built. Wrote pre-build marker: $MARKER"
echo "[ci_post_clone] Stage: Post-Clone completed"
exit 0
