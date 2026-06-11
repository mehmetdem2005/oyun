#!/usr/bin/env bash
# Epic UE container'i ICINDE kosar: Android SDK kur -> L_Dunya uret -> APK paketle.
# Workflow cagirir: docker run -v repo:/proje ... bash /proje/.github/scripts/build-android.sh
# Sonda bulgulari (run 27380979696): imajda Java/Android SDK YOK, SetupAndroid.sh YOK,
# ue4 kullanicisi sudo grubunda, surum receteleri Engine/Config/Android/Android_SDK.json'da.
set -ex

CLIENT_CONFIG="${CLIENT_CONFIG:-Development}"

UE=$(ls -d /home/ue4/UnrealEngine 2>/dev/null || ls -d /opt/UnrealEngine 2>/dev/null)
EDCMD="$UE/Engine/Binaries/Linux/UnrealEditor-Cmd"

# --- Java 17 (UE 5.x Android araç zinciri JDK 17 ister) ---
sudo apt-get update
sudo apt-get install -y --no-install-recommends openjdk-17-jdk-headless unzip curl
JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
export JAVA_HOME

# --- UE'nin istedigi SDK surumlerini motorun kendi recetesinden oku ---
SDKJSON="$UE/Engine/Config/Android/Android_SDK.json"
echo "=== Android_SDK.json ==="; cat "$SDKJSON" || true
PY="$UE/Engine/Binaries/ThirdParty/Python3/Linux/bin/python3"
[ -x "$PY" ] || PY=$(command -v python3 || echo "")
PKGS=""
if [ -n "$PY" ] && [ -f "$SDKJSON" ]; then
  PKGS=$("$PY" - "$SDKJSON" <<'EOF' || true
import json, re, sys
d = json.load(open(sys.argv[1]))
pairs = []
def walk(o, path=""):
    if isinstance(o, dict):
        for k, v in o.items(): walk(v, path + "/" + str(k).lower())
    elif isinstance(o, list):
        for v in o: walk(v, path)
    elif isinstance(o, str):
        pairs.append((path, o))
walk(d)
plat = ndk = bt = None
for p, v in pairs:
    if not plat and re.fullmatch(r"android-\d+", v): plat = v
    if not ndk and "ndk" in p and re.fullmatch(r"[\d.]+|r\d+[a-z]?", v): ndk = v
    if not bt and ("build-tools" in p or "buildtools" in p) and re.fullmatch(r"[\d.]+|latest", v): bt = v
out = ["platform-tools"]
if plat: out.append("platforms;" + plat)
if bt and bt != "latest": out.append("build-tools;" + bt)
if ndk: out.append("ndk;" + ndk)
print(" ".join(out))
EOF
)
fi
# Recete okunamadiysa bilinen UE5 varsayilanlarina dus:
if [ -z "$PKGS" ] || [ "$PKGS" = "platform-tools" ]; then
  PKGS="platform-tools platforms;android-34 build-tools;34.0.0 ndk;25.1.8937393"
fi
echo "Kurulacak SDK paketleri: $PKGS"

# --- Android cmdline-tools + sdkmanager ---
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
curl -sSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/clt.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --licenses >/dev/null || true
# shellcheck disable=SC2086
"$SDKM" $PKGS
NDKROOT=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1 || true)
export NDKROOT
export NDK_ROOT="$NDKROOT"
echo "ANDROID_HOME=$ANDROID_HOME  NDKROOT=$NDKROOT  JAVA_HOME=$JAVA_HOME"

# --- L_Dunya haritasi: CLAUDE.md'deki tek manuel editor adimini CI'da otomatik uret ---
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
