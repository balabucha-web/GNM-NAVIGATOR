#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/de/balabucha/reisepilot/DiscoverScreen.kt"
text = path.read_text()

import_line = "import androidx.compose.ui.platform.LocalContext\n"
if "import androidx.compose.ui.platform.testTag\n" not in text:
    if import_line not in text:
        raise SystemExit("LocalContext import not found")
    text = text.replace(import_line, import_line + "import androidx.compose.ui.platform.testTag\n", 1)

old = "modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),"
new = "modifier = Modifier.fillMaxWidth().testTag(destinationCardTag(place)).clickable(onClick = onClick),"
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("Place card modifier not found")

helper = '''\ninternal fun destinationCardTag(place: TravelPlace): String =\n    "destination-card:${place.region.name}:${place.title}"\n'''
if "internal fun destinationCardTag" not in text:
    text += helper

path.write_text(text)
print("Applied stable destination card tags")
