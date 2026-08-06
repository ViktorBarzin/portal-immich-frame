#!/usr/bin/env bash
#
# Build the debug APK in a throwaway container — no host JDK/Android SDK install
# (devvm policy: tooling runs in containers). The Android SDK, a Gradle
# distribution, and the Gradle dependency cache are kept in named docker volumes
# so rebuilds are fast. Disk-conscious: ~2.7 GB across image + volumes.
#
# Runs the unit tests before assembling — this repo has no CI, so this script is the
# only gate; a red test fails the build.
#
# Usage: scripts/build-apk.sh        (debug APK, default London frame)
#        FRAME_URL=https://highlights-immich-emo.viktorbarzin.me scripts/build-apk.sh
#                                    (sets the DEFAULT this APK ships with; a device
#                                     can also be re-pointed at runtime — see README)
# Output: app/build/outputs/apk/debug/app-debug.apk
#
set -euo pipefail

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="eclipse-temurin:17-jdk"
SDK_VOL="portalframe-android-sdk"
GRADLE_VOL="portalframe-gradle-cache"
HOME_VOL="portalframe-android-home"   # persists ~/.android/debug.keystore so the
                                      # debug signature is STABLE across rebuilds
                                      # (otherwise `adb install -r` fails with
                                      # INSTALL_FAILED_UPDATE_INCOMPATIBLE).
GRADLE_VERSION="8.7"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

docker volume create "$SDK_VOL"    >/dev/null
docker volume create "$GRADLE_VOL" >/dev/null
docker volume create "$HOME_VOL"   >/dev/null

docker run --rm \
  -v "$PROJ_DIR":/project \
  -v "$SDK_VOL":/sdk \
  -v "$GRADLE_VOL":/root/.gradle \
  -v "$HOME_VOL":/root/.android \
  -w /project \
  -e ANDROID_SDK_ROOT=/sdk \
  -e ANDROID_HOME=/sdk \
  -e FRAME_URL="${FRAME_URL:-}" \
  "$IMAGE" bash -euo pipefail -c '
    export DEBIAN_FRONTEND=noninteractive
    command -v unzip >/dev/null || { apt-get update -qq && apt-get install -y -qq unzip curl >/dev/null; }

    if [ ! -x /sdk/cmdline-tools/latest/bin/sdkmanager ]; then
      echo ">> Installing Android cmdline-tools..."
      curl -fsSL -o /tmp/clt.zip "'"$CMDLINE_TOOLS_URL"'"
      rm -rf /tmp/clt && unzip -q -o /tmp/clt.zip -d /tmp/clt
      mkdir -p /sdk/cmdline-tools && rm -rf /sdk/cmdline-tools/latest
      mv /tmp/clt/cmdline-tools /sdk/cmdline-tools/latest
    fi
    export PATH="/sdk/cmdline-tools/latest/bin:/sdk/platform-tools:$PATH"

    echo ">> Accepting licenses + installing SDK packages..."
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

    if [ ! -x /sdk/gradle-'"$GRADLE_VERSION"'/bin/gradle ]; then
      echo ">> Downloading Gradle '"$GRADLE_VERSION"'..."
      curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-'"$GRADLE_VERSION"'-bin.zip"
      unzip -q -o /tmp/gradle.zip -d /sdk/
    fi
    export PATH="/sdk/gradle-'"$GRADLE_VERSION"'/bin:$PATH"

    # Generate the committed wrapper once, then build through it.
    [ -x ./gradlew ] || gradle wrapper --gradle-version '"$GRADLE_VERSION"' --distribution-type bin >/dev/null
    echo ">> Running unit tests..."
    ./gradlew --no-daemon testDebugUnitTest ${FRAME_URL:+-PframeUrl="$FRAME_URL"}

    echo ">> Building debug APK (FRAME_URL=${FRAME_URL:-<default London>})..."
    ./gradlew --no-daemon assembleDebug ${FRAME_URL:+-PframeUrl="$FRAME_URL"}
  '

echo
echo ">> Done. Artifact:"
ls -la "$PROJ_DIR/app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null \
  || { echo "BUILD FAILED — no APK produced"; exit 1; }
