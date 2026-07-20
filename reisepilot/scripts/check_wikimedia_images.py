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
    "Casa Batllo Barcelona facade",
    "Sant Joan de Caselles Andorra",
    "Atelier des Lumieres Paris",
    "Anse de Paulilles France",
]


def request_json(base: str, params: dict[str, str]) -> dict:
    url = base + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "ReisePilot/4.2 CI image smoke test",
            "Accept": "application/json",
            "Accept-Language": "de,en;q=0.8",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def usable(url: object) -> bool:
    if not isinstance(url, str) or not url.startswith("https://"):
        return False
    clean = url.split("?", 1)[0].lower()
    return not clean.endswith((".pdf", ".djvu", ".tif", ".tiff", ".svg", ".gif"))


def wikipedia_image(query: str) -> str | None:
    payload = request_json(
        "https://en.wikipedia.org/w/api.php",
        {
            "action": "query",
            "generator": "search",
            "gsrnamespace": "0",
            "gsrlimit": "6",
            "gsrsearch": query,
            "prop": "pageimages",
            "piprop": "thumbnail",
            "pithumbsize": "1000",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        },
    )
    for page in payload.get("query", {}).get("pages", []):
        candidate = (page.get("thumbnail") or {}).get("source")
        if usable(candidate):
            return candidate
    return None


def commons_image(query: str) -> str | None:
    payload = request_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "generator": "search",
            "gsrnamespace": "6",
            "gsrlimit": "12",
            "gsrsearch": f"{query} -logo -flag -map -icon",
            "prop": "imageinfo",
            "iiprop": "url|mime|size",
            "iiurlwidth": "1000",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        },
    )
    for page in payload.get("query", {}).get("pages", []):
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        info = infos[0]
        if info.get("mime") not in {"image/jpeg", "image/png", "image/webp"}:
            continue
        if 0 < int(info.get("width") or 0) < 400:
            continue
        candidate = info.get("thumburl") or info.get("url")
        if usable(candidate):
            return candidate
    return None


def find_image(query: str) -> str | None:
    return wikipedia_image(query) or commons_image(query)


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
            print(f"PASS {query}: {result[:120]}")
        else:
            failures.append(query)
    if failures:
        print("Missing Wikimedia image results:", ", ".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
