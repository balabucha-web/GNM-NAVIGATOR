from pathlib import Path

path = Path("app/src/main/java/de/balabucha/reisepilot/FuelPriceClient.kt")
text = path.read_text(encoding="utf-8")

old = """    private val spain = listOf(
        GeoPoint(43.80, -9.50), GeoPoint(43.45, -1.70), GeoPoint(42.85, 0.70),
        GeoPoint(42.42, 3.30), GeoPoint(36.00, -5.20), GeoPoint(36.00, -7.50),
        GeoPoint(41.90, -9.50)
    )
"""
new = """    private val spain = listOf(
        GeoPoint(43.80, -9.50), GeoPoint(43.45, -1.70), GeoPoint(42.85, 0.70),
        GeoPoint(42.42, 3.30), GeoPoint(41.80, 3.20), GeoPoint(41.00, 2.90),
        GeoPoint(39.00, 0.30), GeoPoint(37.00, -1.80), GeoPoint(36.00, -5.20),
        GeoPoint(36.00, -7.50), GeoPoint(41.90, -9.50)
    )
"""

if old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("PASS: Spain Mediterranean coastline polygon corrected")
elif new in text:
    print("PASS: Spain coastline hotfix already applied")
else:
    raise SystemExit("Spain polygon block mismatch; refusing an unsafe partial edit")
