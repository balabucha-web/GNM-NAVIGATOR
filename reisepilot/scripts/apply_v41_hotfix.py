from pathlib import Path

path = Path("app/src/main/java/de/balabucha/reisepilot/MoreHubScreen.kt")
text = path.read_text(encoding="utf-8")
old = '            MoreScreen(activity, snapshot, Modifier.fillMaxSize().testTag("technical-settings-screen"))'
new = '            MoreScreen(activity, snapshot, Modifier.fillMaxSize())'
if old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("PASS: duplicate technical-settings container tag removed")
elif new in text:
    print("PASS: duplicate container tag already removed")
else:
    raise SystemExit("MoreHubScreen technical marker mismatch")
