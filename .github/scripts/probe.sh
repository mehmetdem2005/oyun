#!/usr/bin/env bash
# Epic UE container'i ICINDE kosar: Android arac/surum kesfi. Gecici teshis araci.
set -x
id; whoami; echo "HOME=$HOME"
UE=$(ls -d /home/ue4/UnrealEngine 2>/dev/null || ls -d /opt/UnrealEngine 2>/dev/null); echo "UE=$UE"
echo "=== InstalledBuild.txt (desteklenen platformlarin otoritesi) ==="
cat "$UE/Engine/Build/InstalledBuild.txt" 2>/dev/null || echo "InstalledBuild.txt YOK (kaynak derleme gibi davranir)"
echo "=== Installed platform bilgisi ==="
find "$UE/Engine/Intermediate" -maxdepth 3 -iname "*Installed*" 2>/dev/null | head
grep -rn "InstalledPlatform" "$UE/Engine/Config/BaseEngine.ini" 2>/dev/null | head -20
echo "=== Android motor ikilikleri var mi ==="
ls "$UE/Engine/Binaries/Android" 2>/dev/null | head || echo "Engine/Binaries/Android YOK"
ls "$UE/Engine/Intermediate/Build/Android" 2>/dev/null | head -5 || echo "Intermediate/Build/Android YOK"
ls "$UE/Engine/Plugins/Runtime/AndroidPermission" 2>/dev/null >/dev/null && echo "AndroidPermission eklentisi VAR"
ls "$UE/Engine" || true
ls "$UE/Engine/Extras" 2>/dev/null | head -30 || echo "Extras YOK"
find "$UE" -maxdepth 4 -iname "SetupAndroid*" 2>/dev/null | head
env | grep -iE "android|java|ndk|sdk" || echo "ilgili env yok"
ls /home/ue4 2>/dev/null; ls /opt 2>/dev/null
command -v sdkmanager javac java adb || echo "java-android araclari bulunamadi"
echo "=== UBT Android kaynaklari ==="
ls "$UE/Engine/Source/Programs/UnrealBuildTool/Platform/Android/" 2>/dev/null | head -20 || echo "UBT Android kaynagi YOK"
echo "=== UBT istedigi surumler ==="
VERSFILE="$UE/Engine/Source/Programs/UnrealBuildTool/Platform/Android/AndroidPlatformSDK.Versioning.cs"
[ -f "$VERSFILE" ] && grep -nE 'r[0-9]{2}[a-z]?|MinimumSDKLevel|BuildTools|android-[0-9]+|GetMainVersion|MaxVersion|MinVersion' "$VERSFILE" | head -40
grep -rnE '"r[0-9]+[a-z]?"' "$UE/Engine/Source/Programs/UnrealBuildTool/Platform/Android/"*.cs 2>/dev/null | head -10
echo "=== Android toolchain cs dosyalari ==="
find "$UE/Engine" -maxdepth 5 -iname "*.cs" -path "*Android*" 2>/dev/null | head -20
echo "=== Android_SDK config ==="
grep -rn "SDKLevel\|NDKLevel\|BuildToolsVersion" "$UE/Engine/Config/Android/"* 2>/dev/null | head -20
grep -rn "SDKLicenses\|platform-tools\|ndk;" "$UE/Engine/Build/Android/"* 2>/dev/null | head -10
df -h / 2>/dev/null
true
