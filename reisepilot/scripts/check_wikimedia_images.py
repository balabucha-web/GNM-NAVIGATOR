#!/usr/bin/env python3
"""Live smoke test for exact, multi-image Wikimedia POI galleries."""

import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
import urllib.error


CHECKS = [
    ("Collioure", 3),
    ("Sagrada Família", 3),
    ("Roc del Quer", 3),
    ("Sant Joan de Caselles", 3),
    ("Casa Batlló", 3),
    ("Ménagerie du Jardin des Plantes", 3),
    ("Atelier des Lumières", 3),
    ("Anse de Paulilles", 1),
    ("Aquarium de Canet-en-Roussillon", 1),
]

STOP_WORDS = {
    "and", "the", "des", "der", "die", "das", "und", "de", "du", "la", "le", "les", "del",
    "paris", "barcelona", "andorra", "france", "spain", "canet", "roussillon", "museum", "musee",
}


class WikimediaRateLimited(RuntimeError):
    """The service answered, but deliberately refused this audit request."""


def request_json(base: str, params: dict[str, str]) -> dict:
    url = base + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "ReisePilot/4.5 exact gallery audit",
            "Accept": "application/json",
            "Accept-Language": "de,en;q=0.8",
        },
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return json.load(response)
        except urllib.error.HTTPError as exc:
            if exc.code != 429:
                raise
            if attempt == 3:
                raise WikimediaRateLimited("HTTP 429 after four attempts") from exc
            retry_after = int(exc.headers.get("Retry-After", "0") or 0)
            time.sleep(max(retry_after, 2 ** (attempt + 1)))
    raise RuntimeError("unreachable")


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFD", value)
    value = "".join(char for char in value if unicodedata.category(char) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def tokens(value: str) -> set[str]:
    return {token for token in normalize(value).split() if len(token) >= 4 and token not in STOP_WORDS}


def relevant(label: str, query: str) -> bool:
    return bool(tokens(label) & tokens(query))


def usable(url: object) -> bool:
    if not isinstance(url, str) or not url.startswith("https://"):
        return False
    clean = url.split("?", 1)[0].lower()
    if any(marker in clean for marker in (".svg/", ".gif/", ".tif/", ".tiff/")):
        return False
    return not clean.endswith((".pdf", ".djvu", ".tif", ".tiff", ".svg", ".gif"))


def exact_commons_images(query: str) -> list[str]:
    payload = request_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "generator": "search",
            "gsrnamespace": "6",
            "gsrlimit": "24",
            "gsrsearch": f"{query} -logo -flag -map -icon -diagram",
            "prop": "imageinfo",
            "iiprop": "url|mime|size",
            "iiurlwidth": "1000",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        },
    )
    results: list[str] = []
    for page in payload.get("query", {}).get("pages", []):
        if not relevant(str(page.get("title") or ""), query):
            continue
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        info = infos[0]
        if info.get("mime") not in {"image/jpeg", "image/png", "image/webp"}:
            continue
        if 0 < int(info.get("width") or 0) < 500:
            continue
        candidate = info.get("thumburl") or info.get("url")
        if usable(candidate):
            results.append(candidate)
    return results


def category_images(query: str) -> list[str]:
    payload = request_json(
        "https://commons.wikimedia.org/w/api.php",
        {
            "action": "query",
            "generator": "search",
            "gsrnamespace": "14",
            "gsrlimit": "8",
            "gsrsearch": query,
            "prop": "categoryinfo",
            "format": "json",
            "formatversion": "2",
            "origin": "*",
        },
    )
    categories: list[tuple[int, int, str]] = []
    wanted = tokens(query)
    harmless = {"category", "interior", "interiors", "exterior", "exteriors", "views", "details", "gardens"}
    for page in payload.get("query", {}).get("pages", []):
        title = str(page.get("title") or "")
        file_count = int((page.get("categoryinfo") or {}).get("files") or 0)
        lower = normalize(title)
        if file_count <= 0 or not relevant(title, query):
            continue
        if any(noise in lower for noise in ("metrostation", "railway station", "gare de", "war memorial", " aoc")):
            continue
        category_tokens = tokens(title)
        extra = category_tokens - wanted - harmless
        precision = len(category_tokens & wanted) * 30 - len(extra) * 24 + min(file_count // 8, 10)
        categories.append((precision, file_count, title))

    results: list[str] = []
    for _, _, category in sorted(categories, reverse=True)[:1]:
        members = request_json(
            "https://commons.wikimedia.org/w/api.php",
            {
                "action": "query",
                "generator": "categorymembers",
                "gcmtitle": category,
                "gcmnamespace": "6",
                "gcmtype": "file",
                "gcmlimit": "18",
                "prop": "imageinfo",
                "iiprop": "url|mime|size",
                "iiurlwidth": "1000",
                "format": "json",
                "formatversion": "2",
                "origin": "*",
            },
        )
        for page in members.get("query", {}).get("pages", []):
            infos = page.get("imageinfo") or []
            if not infos:
                continue
            info = infos[0]
            if info.get("mime") not in {"image/jpeg", "image/png", "image/webp"}:
                continue
            if 0 < int(info.get("width") or 0) < 500:
                continue
            candidate = info.get("thumburl") or info.get("url")
            if usable(candidate):
                results.append(candidate)
    return results


def find_images(query: str, required: int) -> list[str]:
    results = category_images(query)
    if len(results) < required:
        results += exact_commons_images(query)
    return list(dict.fromkeys(results))


def main() -> int:
    failures: list[str] = []
    throttled: list[str] = []
    passed = 0
    for query, required in CHECKS:
        results: list[str] = []
        for attempt in range(2):
            try:
                results = find_images(query, required)
                if len(results) >= required:
                    break
            except WikimediaRateLimited as exc:
                print(f"INCONCLUSIVE {query}: {exc}")
                throttled.append(query)
                break
            except Exception as exc:
                if attempt == 1:
                    print(f"ERROR {query}: {exc}")
                time.sleep(1)
        if len(results) >= required:
            print(f"PASS {query}: {len(results)} exact usable images")
            passed += 1
        elif query not in throttled:
            failures.append(f"{query} ({len(results)}/{required})")
        time.sleep(0.7)
    if failures:
        print("Incomplete exact Wikimedia galleries:", ", ".join(failures))
        return 1
    minimum_live_evidence = (len(CHECKS) + 1) // 2
    if passed < minimum_live_evidence:
        print(f"Insufficient live evidence: only {passed}/{len(CHECKS)} checks completed")
        return 1
    if throttled:
        print(
            "Wikimedia throttled individual checks; remaining live galleries passed:",
            ", ".join(throttled),
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
