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
#          sample/build/xcode-frameworks/Release/iphoneos/KmpBleSample.framework
#      (Xcode's FRAMEWORK_SEARCH_PATHS = .../xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME))
#   4. Writes a marker file; the "Compile Kotlin Framework" build phase checks
#      it and skips its own Gradle invocation.
set -e

# ---------------------------------------------------------------------------
# 0) Locate paths
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"            # repo root (iosApp is a sibling)
GRADLEW="$REPO_DIR/gradlew"
FRAMEWORK_SRC="$REPO_DIR/sample/build/bin/iosArm64/releaseFramework/KmpBleSample.framework"
FRAMEWORKS_DIR="$REPO_DIR/sample/build/xcode-frameworks"
FRAMEWORK_DEST="$FRAMEWORKS_DIR/Release/iphoneos"
MARKER="$FRAMEWORKS_DIR/.xcode-cloud-prebuild"

echo "[ci_post_clone] Stage: Post-Clone activated"
echo "[ci_post_clone] Repository path: $REPO_DIR"
echo "[ci_post_clone] CI action: ${CI_XCODEBUILD_ACTION:-unknown}"

# ---------------------------------------------------------------------------
# 1) Resolve a working Java runtime (JDK 21)
# ---------------------------------------------------------------------------
java_works() {
  [ -x "$1" ] && "$1" -version >/dev/null 2>&1
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
  JAVA_DIR="$REPO_DIR/ci_scripts/.jdk"

  echo "[ci_post_clone] Downloading Temurin JDK 21 (${OS}/${ARCH}) from Adoptium..."
  mkdir -p "$JAVA_DIR"
  TARBALL="$JAVA_DIR/jdk.tar.gz"
  curl -fL --retry 3 -o "$TARBALL" "$JAVA_URL"
  tar xzf "$TARBALL" -C "$JAVA_DIR" --strip-components=1
  rm -f "$TARBALL"

  # macOS Temurin tarballs are a ".jdk" bundle with java under Contents/Home;
  # other OSes put bin/java at the top level. Normalize to JAVA_DIR/bin/java.
  if [ -x "$JAVA_DIR/Contents/Home/bin/java" ]; then
    for d in bin Contents lib include; do
      if [ -e "$JAVA_DIR/Contents/Home/$d" ]; then
        mv "$JAVA_DIR/Contents/Home/$d" "$JAVA_DIR/$d"
      fi
    done
  fi

  if [ -x "$JAVA_DIR/bin/java" ]; then
    JAVA_CMD="$JAVA_DIR/bin/java"
    echo "[ci_post_clone] Installed Temurin JDK 21 at: $JAVA_DIR"
  fi
fi

if [ -z "$JAVA_CMD" ]; then
  echo "[ci_post_clone] error: Unable to obtain a working Java runtime (JDK 21)." >&2
  exit 1
fi

JAVA_HOME="$(cd "$(dirname "$JAVA_CMD")/.." && pwd)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "[ci_post_clone] Using Java: $JAVA_CMD"

# ---------------------------------------------------------------------------
# 2) Build the release device framework
# ---------------------------------------------------------------------------
echo "[ci_post_clone] Building release iosArm64 framework (linkReleaseFrameworkIosArm64)..."
(
  cd "$REPO_DIR"
  "$GRADLEW" :sample:linkReleaseFrameworkIosArm64 --console=plain
)

if [ ! -d "$FRAMEWORK_SRC" ]; then
  echo "[ci_post_clone] error: Build succeeded but framework not found at $FRAMEWORK_SRC" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 3) Place it where Xcode resolves at archive time (Release + iphoneos)
# ---------------------------------------------------------------------------
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
