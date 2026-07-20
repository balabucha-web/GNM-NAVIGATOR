#!/usr/bin/env bash
set -uo pipefail

SUMMARY="emulator-summary.txt"
echo "PHASE emulator launch requested" >> "$SUMMARY"

emulator -avd reisepilot_api34 \
  -no-window -no-snapshot -noaudio -no-boot-anim \
  -gpu swiftshader_indirect -accel on -camera-back none \
  > emulator-boot.log 2>&1 &
EMULATOR_PID=$!
echo "$EMULATOR_PID" > emulator.pid

adb wait-for-device
for _ in $(seq 1 84); do
  BOOTED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOTED" = "1" ]; then
    break
  fi
  sleep 5
done

BOOTED=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
if [ "$BOOTED" != "1" ]; then
  echo "RESULT emulator boot timeout" >> "$SUMMARY"
  adb devices -l > emulator-devices.txt 2>&1 || true
  exit 1
fi

echo "PHASE emulator booted" >> "$SUMMARY"
adb devices -l | tee emulator-devices.txt
adb shell getprop ro.build.version.sdk | tee emulator-api.txt
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c
adb emu geo fix 11.4000 53.6400

set +e
gradle :app:connectedDebugAndroidTest --stacktrace 2>&1 | tee emulator-test.log
CODE=${PIPESTATUS[0]}
set -e
echo "PHASE instrumentation exit=$CODE" >> "$SUMMARY"

adb shell am force-stop de.balabucha.reisepilot || true
adb shell am start -n de.balabucha.reisepilot/.MainActivity | tee emulator-launch.txt
sleep 4
adb shell monkey -p de.balabucha.reisepilot \
  --throttle 90 --pct-syskeys 0 --pct-appswitch 0 -v 250 \
  > monkey.log 2>&1 || true
sleep 3
adb logcat -d > emulator-logcat.txt

if grep -E 'FATAL EXCEPTION|Process: de\.balabucha\.reisepilot.*has died|ANR in de\.balabucha\.reisepilot' emulator-logcat.txt; then
  echo "RESULT app crash or ANR detected" >> "$SUMMARY"
  CODE=1
else
  echo "RESULT no ReisePilot crash or ANR in logcat" >> "$SUMMARY"
fi

adb emu kill || true
exit "$CODE"
