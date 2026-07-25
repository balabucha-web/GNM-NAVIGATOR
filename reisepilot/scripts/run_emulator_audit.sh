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

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [ "$CODE" -eq 0 ]; then
  echo "PHASE reinstall app and instrumentation for responsive checks" >> "$SUMMARY"
  if "$ADB_BIN" install -r "$APP_APK" 2>&1 | tee packing-responsive-app-install.log \
    && "$ADB_BIN" install -r "$TEST_APK" 2>&1 | tee packing-responsive-test-install.log; then
    echo "PHASE responsive packing-list checks" >> "$SUMMARY"
    for SPEC in "720x1280:320:compact" "1080x2400:420:standard" "1440x3200:560:large"; do
      IFS=: read -r SIZE DENSITY LABEL <<< "$SPEC"
      "$ADB_BIN" shell wm size "$SIZE"
      "$ADB_BIN" shell wm density "$DENSITY"
      "$ADB_BIN" shell am force-stop de.balabucha.reisepilot || true
      set +e
      timeout 180 "$ADB_BIN" shell am instrument -w \
        -e class de.balabucha.reisepilot.PackingResponsiveLayoutTest \
        de.balabucha.reisepilot.test/androidx.test.runner.AndroidJUnitRunner \
        2>&1 | tee "packing-responsive-${LABEL}.log"
      SIZE_CODE=${PIPESTATUS[0]}
      set -e
      "$ADB_BIN" exec-out screencap -p > "packing-responsive-${LABEL}.png" || true
      if [ "$SIZE_CODE" -ne 0 ] || ! grep -q 'OK (1 test)' "packing-responsive-${LABEL}.log"; then
        echo "RESULT responsive packing-list $LABEL failed" >> "$SUMMARY"
        CODE=1
      else
        echo "RESULT responsive packing-list $LABEL passed" >> "$SUMMARY"
      fi
    done
    "$ADB_BIN" shell wm size reset || true
    "$ADB_BIN" shell wm density reset || true
  else
    echo "RESULT responsive packing-list test installation failed" >> "$SUMMARY"
    CODE=1
  fi
fi

# A real-user driver audit: activate the journey, feed multiple GPS states and
# capture the screens a driver actually sees. This is intentionally separate
# from Compose assertions so layout and visual plausibility can be reviewed.
click_text() {
  local needle="$1"
  local safe
  safe=$(echo "$needle" | tr ' /:äöüÄÖÜß' '___________')
  "$ADB_BIN" shell uiautomator dump /sdcard/driver-window.xml >/dev/null 2>&1 || return 1
  "$ADB_BIN" pull /sdcard/driver-window.xml "driver-${safe}.xml" >/dev/null 2>&1 || return 1
  local point
  point=$(python3 - "driver-${safe}.xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2].lower()
root = ET.parse(path).getroot()
candidates=[]
for node in root.iter('node'):
    text=(node.attrib.get('text','')+' '+node.attrib.get('content-desc','')).strip()
    if needle in text.lower():
        b=node.attrib.get('bounds','')
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            area=(x2-x1)*(y2-y1)
            candidates.append((0 if node.attrib.get('clickable')=='true' else 1, area, (x1+x2)//2, (y1+y2)//2, text))
if not candidates:
    sys.exit(2)
candidates.sort()
_,_,x,y,text=candidates[0]
print(f'{x} {y}')
PY
  ) || return 1
  read -r x y <<< "$point"
  "$ADB_BIN" shell input tap "$x" "$y"
  sleep 2
}

click_text_with_scroll() {
  local needle="$1"
  if click_text "$needle"; then
    return 0
  fi
  for _ in 1 2 3 4; do
    "$ADB_BIN" shell input swipe 430 1500 430 650 550 >/dev/null 2>&1 || true
    sleep 1
    if click_text "$needle"; then
      return 0
    fi
  done
  return 1
}

capture_driver() {
  local label="$1"
  "$ADB_BIN" exec-out screencap -p > "driver-${label}.png" || true
  "$ADB_BIN" shell uiautomator dump "/sdcard/driver-${label}.xml" >/dev/null 2>&1 || true
  "$ADB_BIN" pull "/sdcard/driver-${label}.xml" "driver-${label}.xml" >/dev/null 2>&1 || true
}

"$ADB_BIN" uninstall de.balabucha.reisepilot >/dev/null 2>&1 || true
"$ADB_BIN" install -r "$APP_APK" | tee emulator-app-install.txt
"$ADB_BIN" shell pm grant de.balabucha.reisepilot android.permission.ACCESS_FINE_LOCATION || true
"$ADB_BIN" shell pm grant de.balabucha.reisepilot android.permission.ACCESS_COARSE_LOCATION || true
"$ADB_BIN" shell pm grant de.balabucha.reisepilot android.permission.POST_NOTIFICATIONS || true
"$ADB_BIN" emu geo fix 11.4000 53.6400 0 10 0 180 || "$ADB_BIN" emu geo fix 11.4000 53.6400
"$ADB_BIN" shell am start -n de.balabucha.reisepilot/.MainActivity | tee emulator-launch.txt
sleep 6
capture_driver "00-preparation"

DRIVER_CODE=0
if click_text_with_scroll "Testfahrt starten"; then
  echo "DRIVER start control activated after current-leg review" | tee -a driver-audit.log
else
  echo "DRIVER start control not found after scrolling" | tee -a driver-audit.log
  DRIVER_CODE=1
fi
sleep 6
capture_driver "01-active-schwerin"

# Simulate normal motorway movement. The extended geo command supplies velocity
# and heading where supported; the fallback still moves the device position.
for FIX in \
  "11.2500 53.5000 0 10 27 190" \
  "10.9000 53.1000 0 10 29 205" \
  "10.1000 52.5000 0 10 31 210" \
  "9.7200 52.3700 0 10 28 210"; do
  "$ADB_BIN" emu geo fix $FIX >/dev/null 2>&1 || "$ADB_BIN" emu geo fix $(echo "$FIX" | awk '{print $1, $2}') >/dev/null 2>&1 || true
  sleep 3
done
capture_driver "02-driving-hannover"

# Scroll the dashboard as a driver/passenger would and capture lower live cards.
"$ADB_BIN" shell input swipe 430 1500 430 520 700 || true
sleep 3
capture_driver "03-driving-dashboard-lower"

if click_text "Karte"; then
  sleep 7
  capture_driver "04-live-map"
else
  echo "DRIVER map tab not found" | tee -a driver-audit.log
  DRIVER_CODE=1
fi

if click_text "Ziele"; then
  sleep 8
  capture_driver "05-destinations-top"
  "$ADB_BIN" shell input swipe 430 1560 430 460 800 || true
  sleep 4
  capture_driver "06-destinations-scrolled"
else
  echo "DRIVER destinations tab not found" | tee -a driver-audit.log
  DRIVER_CODE=1
fi

if click_text "Start"; then
  sleep 4
  capture_driver "07-return-live-start"
else
  echo "DRIVER start tab not found" | tee -a driver-audit.log
  DRIVER_CODE=1
fi

"$ADB_BIN" logcat -d > emulator-logcat.txt
if grep -E 'FATAL EXCEPTION|Process: de\.balabucha\.reisepilot.*has died|ANR in de\.balabucha\.reisepilot' emulator-logcat.txt; then
  echo "RESULT app crash or ANR detected" >> "$SUMMARY"
  CODE=1
else
  echo "RESULT no ReisePilot crash or ANR in logcat" >> "$SUMMARY"
fi
if [ "$DRIVER_CODE" -eq 0 ]; then
  echo "RESULT simulated driver journey captured" >> "$SUMMARY"
else
  echo "RESULT simulated driver journey incomplete" >> "$SUMMARY"
  CODE=1
fi

# Retain legacy evidence names for existing consumers.
cp driver-00-preparation.png emulator-start-screen.png 2>/dev/null || true
cp driver-07-return-live-start.png emulator-after-monkey.png 2>/dev/null || true
"$ADB_BIN" shell monkey -p de.balabucha.reisepilot \
  --throttle 90 --pct-syskeys 0 --pct-appswitch 0 -v 120 \
  > monkey.log 2>&1 || true

"$ADB_BIN" emu kill || true
exit "$CODE"
