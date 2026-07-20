from pathlib import Path

more = Path("app/src/main/java/de/balabucha/reisepilot/MoreScreen.kt")
text = more.read_text(encoding="utf-8")
if "import androidx.compose.ui.platform.testTag" not in text:
    marker = "import androidx.compose.ui.Modifier\n"
    if marker not in text:
        raise SystemExit("MoreScreen import marker missing")
    text = text.replace(marker, marker + "import androidx.compose.ui.platform.testTag\n", 1)
old_heading = '                Text("Mapbox-Kartenzugang", style = MaterialTheme.typography.titleLarge)'
new_heading = '''                Text(
                    "Mapbox-Kartenzugang",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("technical-settings-screen")
                )'''
if old_heading in text:
    text = text.replace(old_heading, new_heading, 1)
elif new_heading not in text:
    raise SystemExit("MoreScreen heading marker mismatch")
more.write_text(text, encoding="utf-8")

test = Path("app/src/androidTest/java/de/balabucha/reisepilot/ReisePilotUserFlowTest.kt")
text = test.read_text(encoding="utf-8")
old = '''        findByScrolling("Technische Einstellungen")
        compose.onNodeWithTag("technical-settings-button", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("technical-settings-screen", useUnmergedTree = true).assertExists()
        findByScrolling("Mapbox-Kartenzugang")
        compose.onNodeWithText("Mapbox-Kartenzugang").assertIsDisplayed()
'''
new = '''        findByScrolling("Technische Einstellungen")
        compose.onNodeWithTag("technical-settings-button", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("technical-settings-screen", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("technical-settings-screen", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Mapbox-Kartenzugang").assertIsDisplayed()
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("Instrumentation test marker mismatch")
test.write_text(text, encoding="utf-8")
print("PASS: technical settings instrumentation semantics hardened")
