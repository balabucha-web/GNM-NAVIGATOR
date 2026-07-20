#!/usr/bin/env bash
set -uo pipefail

SUMMARY="emulator-summary.txt"
echo "PHASE emulator launch requested" >> "$SUMMARY"

EMULATOR_BIN="${ANDROID_HOME}/emulator/emulator"
ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
if [ ! -x "$EMULATOR_BIN" ]; then
  EMULATOR_BIN=$(command -v emulator || true)
fi
if [ ! -x "$ADB_BIN" ]; then
  ADB_BIN=$(command -v adb || true)
fi
if [ -z "$EMULATOR_BIN" ] || [ ! -x "$EMULATOR_BIN" ]; then
  echo "RESULT emulator binary missing" >> "$SUMMARY"
  exit 1
fi
if [ -z "$ADB_BIN" ] || [ ! -x "$ADB_BIN" ]; then
  echo "RESULT adb binary missing" >> "$SUMMARY"
  exit 1
fi
echo "EMULATOR $EMULATOR_BIN" >> "$SUMMARY"
echo "ADB $ADB_BIN" >> "$SUMMARY"

"$EMULATOR_BIN" -avd reisepilot_api34 \
  -no-window -no-snapshot -noaudio -no-boot-anim \
  -gpu swiftshader_indirect -accel on -camera-back none \
  > emulator-boot.log 2>&1 &
EMULATOR_PID=$!
echo "$EMULATOR_PID" > emulator.pid

if ! timeout 180 "$ADB_BIN" wait-for-device; then
  echo "RESULT adb device timeout" >> "$SUMMARY"
  "$ADB_BIN" devices -l > emulator-devices.txt 2>&1 || true
  kill "$EMULATOR_PID" 2>/dev/null || true
  exit 1
fi

for _ in $(seq 1 84); do
  BOOTED=$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOTED" = "1" ]; then
    break
  fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "RESULT emulator process exited during boot" >> "$SUMMARY"
    exit 1
  fi
  sleep 5
done

BOOTED=$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
if [ "$BOOTED" != "1" ]; then
  echo "RESULT Android boot property timeout" >> "$SUMMARY"
  "$ADB_BIN" devices -l > emulator-devices.txt 2>&1 || true
  kill "$EMULATOR_PID" 2>/dev/null || true
  exit 1
fi

echo "PHASE emulator booted" >> "$SUMMARY"
"$ADB_BIN" devices -l | tee emulator-devices.txt
"$ADB_BIN" shell getprop ro.build.version.sdk | tee emulator-api.txt
"$ADB_BIN" shell settings put global window_animation_scale 0
"$ADB_BIN" shell settings put global transition_animation_scale 0
"$ADB_BIN" shell settings put global animator_duration_scale 0
"$ADB_BIN" logcat -c
"$ADB_BIN" emu geo fix 11.4000 53.6400

set +e
timeout 720 gradle :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee emulator-test.log
CODE=${PIPESTATUS[0]}
set -e
echo "PHASE instrumentation exit=$CODE" >> "$SUMMARY"

"$ADB_BIN" shell am force-stop de.balabucha.reisepilot || true
"$ADB_BIN" shell am start -n de.balabucha.reisepilot/.MainActivity | tee emulator-launch.txt
sleep 4
"$ADB_BIN" shell monkey -p de.balabucha.reisepilot \
  --throttle 90 --pct-syskeys 0 --pct-appswitch 0 -v 250 \
  > monkey.log 2>&1 || true
sleep 3
"$ADB_BIN" logcat -d > emulator-logcat.txt

if grep -E 'FATAL EXCEPTION|Process: de\.balabucha\.reisepilot.*has died|ANR in de\.balabucha\.reisepilot' emulator-logcat.txt; then
  echo "RESULT app crash or ANR detected" >> "$SUMMARY"
  CODE=1
else
  echo "RESULT no ReisePilot crash or ANR in logcat" >> "$SUMMARY"
fi

"$ADB_BIN" emu kill || true
exit "$CODE"
