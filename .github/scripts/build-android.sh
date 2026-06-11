#!/usr/bin/env bash
# Epic UE container'ı İÇİNDE koşar: Android SDK hazırla -> L_Dunya üret -> APK paketle.
# Workflow bunu şöyle çağırır: docker run -v repo:/proje ... bash /proje/.github/scripts/build-android.sh
set -ex

CLIENT_CONFIG="${CLIENT_CONFIG:-Development}"

UE=$(ls -d /home/ue4/UnrealEngine 2>/dev/null || ls -d /opt/UnrealEngine 2>/dev/null)
EDCMD="$UE/Engine/Binaries/Linux/UnrealEditor-Cmd"

# --- Android SDK: imajda yoksa UE'nin kendi kurulum betiğiyle kur ---
if [ -z "${ANDROID_HOME:-}" ] && [ ! -d "$HOME/Android/Sdk" ]; then
  (sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless unzip) ||
    (apt-get update && apt-get install -y openjdk-17-jdk-headless unzip) || true
  JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
  export JAVA_HOME
  yes | bash "$UE/Engine/Extras/Android/SetupAndroid.sh" || true
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDKROOT=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1 || true)
export NDKROOT
if [ -z "${JAVA_HOME:-}" ] && command -v javac >/dev/null; then
  JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
  export JAVA_HOME
fi

# --- L_Dunya haritası: CLAUDE.md'deki tek manuel editör adımını CI'da otomatik üret ---
mkdir -p /proje/Content
if [ ! -f /proje/Content/L_Dunya.umap ]; then
  cat > /tmp/mklevel.py <<'PY'
import unreal
les = unreal.get_editor_subsystem(unreal.LevelEditorSubsystem)
les.new_level("/Game/L_Dunya")
les.save_current_level()
PY
  "$EDCMD" /proje/KayipKrallik.uproject -run=pythonscript -script=/tmp/mklevel.py \
    -unattended -nopause -nosplash -stdout || true
  ls -la /proje/Content/
fi

# --- Paketle: build + cook + stage + pak + package ---
"$UE/Engine/Build/BatchFiles/RunUAT.sh" BuildCookRun \
  -project=/proje/KayipKrallik.uproject \
  -platform=Android -cookflavor=ASTC \
  -clientconfig="$CLIENT_CONFIG" \
  -unattended -utf8output \
  -build -cook -stage -pak -package \
  -archive -archivedirectory=/proje/Paket

find /proje/Paket -name '*.apk' -o -name '*.aab' | head
