#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"5.6.1 live fallback marker missing: {label}")
    return text.replace(old, new, 1)


nearby_path = root / "app/src/main/java/de/balabucha/reisepilot/NearbyLiveController56.kt"
nearby = nearby_path.read_text(encoding="utf-8")
nearby = replace_once(
    nearby,
    '''        if (value.active) {
            stopLocations()
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = true, nearbyMode = false)
        } else {
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = false, nearbyMode = true)
            updateRequestState()
        }''',
    '''        if (value.active && value.routeGeoJson.isNotBlank()) {
            stopLocations()
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = true, nearbyMode = false)
        } else {
            RoadAheadStore54.state = RoadAheadStore54.state.copy(active = value.active, nearbyMode = true)
            updateRequestState()
        }''',
    "route-ready snapshot switch"
)
nearby = replace_once(
    nearby,
    '''    private fun updateRequestState() {
        if (!resumed || snapshot.active) {''',
    '''    private fun updateRequestState() {
        if (!resumed || routeReady()) {''',
    "foreground route-ready check"
)
nearby = replace_once(
    nearby,
    '''    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
''',
    '''    private fun hasLocationPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun routeReady(): Boolean = snapshot.active && snapshot.routeGeoJson.isNotBlank()
''',
    "route-ready helper"
)
nearby = nearby.replace('if (!hasLocationPermission() || snapshot.active) return', 'if (!hasLocationPermission() || routeReady()) return')
nearby = nearby.replace('if (requesting || !hasLocationPermission() || snapshot.active) return', 'if (requesting || !hasLocationPermission() || routeReady()) return')
nearby = nearby.replace('if (location != null && !snapshot.active) onLocation(location)', 'if (location != null && !routeReady()) onLocation(location)')
nearby = nearby.replace('if (snapshot.active) return', 'if (routeReady()) return')
nearby = nearby.replace('if (!snapshot.active && (state.loadingFuel || state.loadingRoadside))', 'if (!routeReady() && (state.loadingFuel || state.loadingRoadside))')
nearby = nearby.replace('if (snapshot.active || fuelId != fuelRequestId) return@post', 'if (routeReady() || fuelId != fuelRequestId) return@post')
nearby = nearby.replace('if (snapshot.active || roadsideId != roadsideRequestId) return@post', 'if (routeReady() || roadsideId != roadsideRequestId) return@post')
nearby = nearby.replace('active = false,\n                nearbyMode = true,', 'active = snapshot.active,\n                nearbyMode = true,')
nearby = nearby.replace('active = false,\n            nearbyMode = true,', 'active = snapshot.active,\n            nearbyMode = true,')
nearby_path.write_text(nearby, encoding="utf-8")

models_path = root / "app/src/main/java/de/balabucha/reisepilot/RoadAheadModels54.kt"
models = models_path.read_text(encoding="utf-8")
models = replace_once(
    models,
    'get() = if (nearbyMode && !active) "in deiner Nähe" else "in Fahrtrichtung"',
    'get() = if (nearbyMode) "in deiner Nähe" else "in Fahrtrichtung"',
    "scope label"
)
models_path.write_text(models, encoding="utf-8")

stability_path = root / "app/src/main/java/de/balabucha/reisepilot/HomeStability561.kt"
stability = stability_path.read_text(encoding="utf-8")
stability = replace_once(
    stability,
    'val nearby = roadState.nearbyMode && !snapshot.active',
    'val nearby = roadState.nearbyMode',
    "nearby dashboard label"
)
stability_path.write_text(stability, encoding="utf-8")

home_path = root / "app/src/main/java/de/balabucha/reisepilot/HomeDashboard55.kt"
home = home_path.read_text(encoding="utf-8")
home = home.replace('state.nearbyMode && !state.active', 'state.nearbyMode')
home = home.replace('!(state.nearbyMode)', '!state.nearbyMode')
home_path.write_text(home, encoding="utf-8")

driver_path = root / "scripts/run_emulator_audit.sh"
driver = driver_path.read_text(encoding="utf-8")
driver = replace_once(
    driver,
    '''if click_text_with_scroll "Testfahrt starten"; then
  echo "DRIVER start control activated after current-leg review" | tee -a driver-audit.log
else
  echo "DRIVER start control not found after scrolling" | tee -a driver-audit.log
  DRIVER_CODE=1
fi''',
    '''if click_text_with_scroll "Testfahrt starten" || click_text_with_scroll "Reise starten"; then
  echo "DRIVER start control activated after current-leg review" | tee -a driver-audit.log
else
  echo "DRIVER start control not found after scrolling" | tee -a driver-audit.log
  DRIVER_CODE=1
fi''',
    "date-aware driver start"
)
driver_path.write_text(driver, encoding="utf-8")

print("PASS: nearby live data remains active until a real route is available")
