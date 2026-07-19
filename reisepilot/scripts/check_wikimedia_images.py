#!/usr/bin/env python3
import json
import sys
import time
import urllib.parse
import urllib.request

QUERIES = [
    "Collioure France",
    "Sagrada Familia Barcelona",
    "Roc del Quer Andorra",
    "Eiffel Tower Paris",
]


def find_image(query: str) -> str | None:
    params = {
        "action": "query",
        "generator": "search",
        "gsrnamespace": "6",
        "gsrlimit": "8",
        "gsrsearch": f"{query} -logo -flag -map",
        "prop": "imageinfo",
        "iiprop": "url|mime",
        "iiurlwidth": "1200",
        "format": "json",
        "formatversion": "2",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "ReisePilot/3.4 CI image smoke test"},
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = json.load(response)
    for page in payload.get("query", {}).get("pages", []):
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        candidate = infos[0].get("thumburl") or infos[0].get("url")
        if isinstance(candidate, str) and candidate.startswith("https://"):
            return candidate
    return None


def main() -> int:
    failures: list[str] = []
    for query in QUERIES:
        result = None
        for attempt in range(2):
            try:
                result = find_image(query)
                if result:
                    break
            except Exception as exc:
                if attempt == 1:
                    print(f"ERROR {query}: {exc}")
                time.sleep(1)
        if result:
            print(f"PASS {query}: {result[:100]}")
        else:
            failures.append(query)
    if failures:
        print("Missing Wikimedia image results:", ", ".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
