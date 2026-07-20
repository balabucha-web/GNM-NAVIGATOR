from pathlib import Path

root = Path(__file__).resolve().parents[1]
list_source = (root / "app/src/main/java/de/balabucha/reisepilot/DiscoverScreen.kt").read_text(encoding="utf-8")
detail_source = (root / "app/src/main/java/de/balabucha/reisepilot/PlaceDetailSheet.kt").read_text(encoding="utf-8")

for forbidden in ("AsyncImage", "WikiImageResolver", "produceState"):
    assert forbidden not in list_source, f"Network/image loader found in scrolling list: {forbidden}"

assert "AsyncImage" in detail_source
assert "WikiImageResolver.resolve" in detail_source
assert detail_source.count("AsyncImage") == 1
print("PASS: scrolling list is network-free; one image is loaded only in the open detail.")
