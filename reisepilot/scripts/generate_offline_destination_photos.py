#!/usr/bin/env python3
"""Build one reviewed offline preview per destination from Wikimedia Commons.

The generated sprites make list thumbnails independent from API availability.
Every reused photo keeps source, author and licence metadata in the manifest.
When no exact and defensible photo exists, the generator creates a labelled
destination illustration instead of silently using an unrelated city photo.
"""

from __future__ import annotations

import html
import io
import json
import math
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from threading import Lock

from PIL import Image, ImageDraw, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/balabucha/reisepilot/DiscoverData.kt"
MEDIA = ROOT / "app/src/main/java/de/balabucha/reisepilot/DestinationMedia.kt"
OUTPUT = ROOT / "app/src/main/assets/destination_photos"
CACHE = Path("/tmp/reisepilot-media-cache")
USER_AGENT = (
    "ReisePilot/4.9 offline destination photo builder "
    "(https://github.com/balabucha-web/GNM-NAVIGATOR)"
)
CELL = (480, 270)
COLUMNS = 5

STOP_WORDS = {
    "and", "the", "und", "der", "die", "das", "des", "de", "du", "la", "le", "les",
    "del", "della", "paris", "barcelona", "andorra", "france", "frankreich", "canet",
    "roussillon", "museum", "musee", "centre", "center", "view", "visitor", "site",
}
BLOCKED = {
    "logo", "flag", "blason", "coat of arms", "map of", "location map", "site plan",
    "kaart", "diagram", "pictogram", "poster", "advertisement", "ticket", "brochure",
    "metrostation", "railway station", "gare de", "war memorial", "portrait", "selfie",
    "young woman", "bikini", "sunbathing",
}

# A text search can return an image that mentions the right place without
# showing it. These files were reviewed visually and deliberately beat the
# automatic ranking. Keeping them here also makes regeneration reproducible.
PHOTO_OVERRIDES = {
    "Cap Leucate & Klippenweg": "P1100878Cap Leucate1.JPG",
    "Sagrada Família": "Temple Expiatori de la Sagrada Família (Barcelona) - 66.jpg",
    "Park Güell": "View from Park Güell Terrace.jpg",
    "Gotisches Viertel & Kathedrale": "Main facade of Barcelona Cathedral - 2013.JPG",
    "Montjuïc, Seilbahn & Burg": "Montjuïc Cable Car.jpg",
    "Barceloneta & Strandpromenade": "Promenade and beach, Platja de la Barceloneta, Barcelona, 2015.jpg",
    "Recinte Modernista Sant Pau": "Hospital Sant Pau panorama.jpg",
    "Caldea": "Caldea 20231205 122111.jpg",
    "Avinguda Meritxell": "Avinguda Meritxell Andorra la Vella 20231008 124420.jpg",
    "Roc del Quer": "Mirador del Roc del Quer (43598780091).jpg",
    "Eiffelturm & Trocadéro": "Tour eiffel at sunrise from the trocadero.jpg",
    "Montmartre & Sacré-Cœur": "Basilique du Sacré-Cœur de Montmartre - Paris - GT-01 - 2024.jpg",
    "Arc de Triomphe": "Arc de Triomphe de l'Étoile in July 2011.jpg",
    "Louvre & Tuilerien": "Louvre Museum Wikimedia Commons.jpg",
    "Jardin du Luxembourg": "2017 - Le palais du Luxembourg, dans le jardin du Luxembourg, Paris - 2.jpg",
    "Cité des Sciences": "Inside Cite des Sciences, La Villette.jpg",
}

# Commons currently has photos *near* this destination but none that safely
# identifies the shopping village itself. A labelled illustration is better
# than the unrelated 1974 motorcycle-race result.
FORCE_ILLUSTRATION = {"La Roca Village", "Disneyland Paris"}
REGION_COLOURS = {
    "CANET": (63, 166, 196),
    "BARCELONA": (207, 126, 68),
    "ANDORRA": (89, 133, 101),
    "PARIS": (102, 101, 145),
}
LANGUAGES = {
    "CANET": ("fr", "en", "ca"),
    "BARCELONA": ("ca", "es", "en"),
    "ANDORRA": ("ca", "es", "fr", "en"),
    "PARIS": ("fr", "en"),
}


@dataclass(frozen=True)
class Place:
    region: str
    title: str
    lat: float
    lon: float
    kind: str
    image_query: str
    identities: tuple[str, ...]
    pinned_ids: tuple[str, ...]


@dataclass
class Candidate:
    label: str
    url: str
    page: str
    author: str
    licence: str
    licence_url: str
    width: int
    height: int
    score: int
    trusted: bool = False


_request_lock = Lock()
_last_request = 0.0


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFD", value)
    value = "".join(ch for ch in value if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def tokens(value: str) -> set[str]:
    return {part for part in normalize(value).split() if len(part) >= 4 and part not in STOP_WORDS}


def clean_html(value: str) -> str:
    value = re.sub(r"<[^>]+>", " ", html.unescape(value or ""))
    return re.sub(r"\s+", " ", value).strip()[:180]


def kotlin_unescape(value: str) -> str:
    return bytes(value, "utf-8").decode("unicode_escape") if "\\" in value else value


def parse_places() -> list[Place]:
    data = SOURCE.read_text(encoding="utf-8")
    media = MEDIA.read_text(encoding="utf-8")
    identity_block = media.split("private val identityQueries", 1)[1].split(
        "private val pinnedWikidataIds", 1
    )[0]
    identities = {
        title: tuple(re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', body))
        for title, body in re.findall(
            r'^\s*"([^"]+)"\s+to\s+listOf\(([^)]*)\),?$', identity_block, re.M
        )
    }
    pinned_block = media.split("private val pinnedWikidataIds", 1)[1].split("fun identities", 1)[0]
    pinned = {
        title: tuple(re.findall(r'"(Q\d+)"', body))
        for title, body in re.findall(r'^\s*"([^"]+)"\s+to\s+listOf\(([^)]*)\),?$', pinned_block, re.M)
    }
    pattern = re.compile(
        r'TravelPlace\(TravelRegion\.(\w+),\s*"([^"]+)",.*?'
        r'GeoPoint\(([-\d.]+),\s*([-\d.]+)\),\s*PlaceKind\.(\w+),\s*'
        r'"[^"]*",\s*"([^"]+)"',
        re.S,
    )
    places: list[Place] = []
    for region, title, lat, lon, kind, image_query in pattern.findall(data):
        places.append(
            Place(
                region=region,
                title=title,
                lat=float(lat),
                lon=float(lon),
                kind=kind,
                image_query=image_query,
                identities=identities.get(title, (image_query,)),
                pinned_ids=pinned.get(title, ()),
            )
        )
    if len(places) != 93:
        raise RuntimeError(f"Expected 93 destinations, parsed {len(places)}")
    return places


def request_bytes(url: str, timeout: int = 25) -> bytes:
    global _last_request
    cache_name = __import__("hashlib").sha256(url.encode()).hexdigest()
    cache_file = CACHE / cache_name
    if cache_file.is_file():
        return cache_file.read_bytes()
    for attempt in range(5):
        with _request_lock:
            delay = 0.45 - (time.monotonic() - _last_request)
            if delay > 0:
                time.sleep(delay)
            _last_request = time.monotonic()
        req = urllib.request.Request(
            url,
            headers={"User-Agent": USER_AGENT, "Accept-Language": "de,en;q=0.8"},
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                body = response.read()
            CACHE.mkdir(parents=True, exist_ok=True)
            cache_file.write_bytes(body)
            return body
        except urllib.error.HTTPError as exc:
            if exc.code not in {429, 500, 502, 503, 504} or attempt == 4:
                raise
            retry = int(exc.headers.get("Retry-After", "0") or 0)
            time.sleep(max(retry, 1 + attempt * 1.2))
        except (TimeoutError, urllib.error.URLError):
            if attempt == 4:
                raise
            time.sleep(1 + attempt)
    raise RuntimeError("unreachable")


def api(base: str, **params: str) -> dict:
    params.setdefault("format", "json")
    params.setdefault("formatversion", "2")
    params.setdefault("origin", "*")
    return json.loads(request_bytes(base + "?" + urllib.parse.urlencode(params)))


def distance_km(a: tuple[float, float], b: tuple[float, float]) -> float:
    from math import asin, cos, radians, sin, sqrt
    lat1, lon1 = map(radians, a)
    lat2, lon2 = map(radians, b)
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    return 12742.0 * asin(sqrt(h))


def claim_values(claims: dict, prop: str) -> list:
    values = []
    for claim in claims.get(prop, []):
        value = claim.get("mainsnak", {}).get("datavalue", {}).get("value")
        if value is not None:
            values.append(value)
    return values


def metadata(page: dict, base_score: int, trusted: bool) -> Candidate | None:
    infos = page.get("imageinfo") or []
    if not infos:
        return None
    info = infos[0]
    mime = str(info.get("mime") or "").lower()
    width, height = int(info.get("width") or 0), int(info.get("height") or 0)
    label = str(page.get("title") or "")
    lower = normalize(label)
    if mime not in {"image/jpeg", "image/png", "image/webp"} or min(width, height) < 500:
        return None
    if any(marker in lower for marker in BLOCKED):
        return None
    ext = info.get("extmetadata") or {}
    field = lambda name: clean_html(str((ext.get(name) or {}).get("value") or ""))
    url = str(info.get("thumburl") or info.get("url") or "")
    if not url.startswith("https://"):
        return None
    return Candidate(
        label=label,
        url=url,
        page=str(info.get("descriptionurl") or ""),
        author=field("Artist") or field("Credit"),
        licence=field("LicenseShortName") or "Wikimedia Commons",
        licence_url=field("LicenseUrl"),
        width=width,
        height=height,
        score=base_score,
        trusted=trusted,
    )


def commons_file(filename: str, score: int, trusted: bool = True) -> list[Candidate]:
    root = api(
        "https://commons.wikimedia.org/w/api.php",
        action="query",
        titles="File:" + filename.removeprefix("File:"),
        prop="imageinfo",
        iiprop="url|mime|size|extmetadata",
        iiurlwidth="960",
    )
    return [c for page in root.get("query", {}).get("pages", []) if (c := metadata(page, score, trusted))]


def commons_category(category: str, score: int) -> list[Candidate]:
    root = api(
        "https://commons.wikimedia.org/w/api.php",
        action="query",
        generator="categorymembers",
        gcmtitle="Category:" + category.removeprefix("Category:"),
        gcmnamespace="6",
        gcmtype="file",
        gcmlimit="28",
        prop="imageinfo",
        iiprop="url|mime|size|extmetadata",
        iiurlwidth="960",
    )
    return [c for page in root.get("query", {}).get("pages", []) if (c := metadata(page, score, True))]


def commons_search(query: str, score: int) -> list[Candidate]:
    root = api(
        "https://commons.wikimedia.org/w/api.php",
        action="query",
        generator="search",
        gsrnamespace="6",
        gsrlimit="20",
        gsrsearch=f"{query} -logo -flag -map -icon -diagram",
        prop="imageinfo",
        iiprop="url|mime|size|extmetadata",
        iiurlwidth="960",
    )
    return [c for page in root.get("query", {}).get("pages", []) if (c := metadata(page, score, False))]


def wikidata_candidates(place: Place) -> list[Candidate]:
    ids = list(place.pinned_ids)
    if not ids:
        for identity in place.identities[:2]:
            for language in LANGUAGES[place.region][:2]:
                root = api(
                    "https://www.wikidata.org/w/api.php",
                    action="wbsearchentities",
                    search=identity,
                    language=language,
                    uselang=language,
                    type="item",
                    limit="6",
                )
                ids.extend(row["id"] for row in root.get("search", []) if row.get("id"))
                if ids:
                    break
            if ids:
                break
    ids = list(dict.fromkeys(ids))[:8]
    if not ids:
        return []
    root = api(
        "https://www.wikidata.org/w/api.php",
        action="wbgetentities",
        ids="|".join(ids),
        props="claims|labels",
        languages="de|en|fr|ca|es",
    )
    scored: list[tuple[int, dict]] = []
    for entity in root.get("entities", {}).values():
        claims = entity.get("claims") or {}
        pictures = claim_values(claims, "P18")
        categories = claim_values(claims, "P373")
        if not pictures and not categories:
            continue
        distances = []
        for coord in claim_values(claims, "P625"):
            if isinstance(coord, dict) and "latitude" in coord and "longitude" in coord:
                distances.append(distance_km((place.lat, place.lon), (coord["latitude"], coord["longitude"])))
        maximum = 35 if place.kind == "NATURE" else 15 if place.kind == "SHOPPING" else 20
        if not distances or min(distances) > maximum:
            continue
        labels = " ".join(str(v.get("value") or "") for v in (entity.get("labels") or {}).values())
        overlap = len(tokens(labels) & tokens(" ".join(place.identities)))
        scored.append((900 + overlap * 45 - int(min(distances) * 3), claims))
    if not scored:
        return []
    score, claims = max(scored, key=lambda row: row[0])
    candidates: list[Candidate] = []
    for image in claim_values(claims, "P18")[:2]:
        candidates += commons_file(str(image), score)
    for category in claim_values(claims, "P373")[:1]:
        candidates += commons_category(str(category), score - 120)
    return candidates


def ranked_candidates(place: Place) -> list[Candidate]:
    if place.title in FORCE_ILLUSTRATION:
        return []
    candidates: list[Candidate] = []
    override = PHOTO_OVERRIDES.get(place.title)
    if override:
        try:
            candidates += commons_file(override, 2_000)
        except Exception as exc:
            print(f"WARN {place.title}: reviewed photo {exc}", flush=True)
        if candidates:
            return candidates
    # Coordinate-valid pinned identities are worth the extra Wikidata round-trip.
    # For the remaining entries the exact Commons text query is both faster and
    # less likely to trigger a broad, similarly named entity.
    if place.pinned_ids:
        try:
            candidates += wikidata_candidates(place)
        except Exception as exc:
            print(f"WARN {place.title}: Wikidata {exc}", flush=True)
    for query in list(dict.fromkeys((*place.identities, place.image_query)))[:1]:
        try:
            candidates += commons_search(query, 320)
        except Exception as exc:
            print(f"WARN {place.title}: Commons {exc}", flush=True)
    wanted = tokens(" ".join((place.title, place.image_query, *place.identities)))
    alien = {
        "eiffel", "louvre", "versailles", "disneyland", "sagrada", "guell", "batllo",
        "pedrera", "caldea", "tristaina", "collioure",
    } - wanted
    for candidate in candidates:
        label_tokens = tokens(candidate.label)
        overlap = len(label_tokens & wanted)
        candidate.score += overlap * 42
        candidate.score -= len(label_tokens & alien) * 180
        if not candidate.trusted and overlap == 0:
            candidate.score -= 900
        if candidate.width > candidate.height:
            candidate.score += 12
    unique: dict[str, Candidate] = {}
    for candidate in sorted(candidates, key=lambda value: value.score, reverse=True):
        key = candidate.page or candidate.url.split("?", 1)[0]
        unique.setdefault(key, candidate)
    return [candidate for candidate in unique.values() if candidate.score >= 100]


def download_photo(candidate: Candidate) -> Image.Image:
    image = Image.open(io.BytesIO(request_bytes(candidate.url, timeout=35)))
    image.load()
    return ImageOps.fit(image.convert("RGB"), CELL, method=Image.Resampling.LANCZOS)


def fonts() -> tuple[ImageFont.FreeTypeFont, ImageFont.FreeTypeFont]:
    regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    return ImageFont.truetype(regular, 24), ImageFont.truetype(bold, 34)


def fallback(place: Place) -> Image.Image:
    base = REGION_COLOURS[place.region]
    image = Image.new("RGB", CELL, base)
    draw = ImageDraw.Draw(image)
    for y in range(CELL[1]):
        f = y / CELL[1]
        colour = tuple(int(value * (1 - 0.25 * f) + 245 * 0.25 * f) for value in base)
        draw.line((0, y, CELL[0], y), fill=colour)
    draw.ellipse((350, -70, 500, 80), fill=(255, 211, 102))
    if place.title == "Disneyland Paris":
        draw.rectangle((0, 200, 480, 270), fill=(72, 139, 92))
        draw.rectangle((205, 135, 335, 225), fill=(94, 88, 151))
        for x, width, top in ((180, 45, 155), (235, 55, 105), (310, 42, 145)):
            draw.rectangle((x, top, x + width, 225), fill=(116, 109, 177))
            draw.polygon(
                [(x - 5, top), (x + width // 2, top - 55), (x + width + 5, top)],
                fill=(239, 190, 83),
            )
        draw.ellipse((251, 175, 279, 225), fill=(30, 42, 70))
    elif place.region == "ANDORRA":
        draw.polygon([(0, 215), (120, 75), (220, 210), (355, 55), (480, 215)], fill=(62, 101, 76))
    elif place.region in {"CANET", "BARCELONA"}:
        draw.rectangle((0, 190, 480, 270), fill=(47, 139, 181))
        draw.line((0, 210, 480, 210), fill="white", width=5)
    else:
        draw.rectangle((0, 205, 480, 270), fill=(84, 143, 174))
        draw.polygon([(240, 45), (205, 215), (225, 215), (240, 145), (255, 215), (275, 215)], fill=(41, 51, 72))
    regular, bold = fonts()
    words = place.title.split()
    lines, current = [], ""
    for word in words:
        proposal = f"{current} {word}".strip()
        if draw.textbbox((0, 0), proposal, font=bold)[2] > 420 and current:
            lines.append(current)
            current = word
        else:
            current = proposal
    if current:
        lines.append(current)
    lines = lines[:3]
    height = len(lines) * 40 + 42
    draw.rounded_rectangle((18, 18, 462, 30 + height), radius=18, fill=(15, 32, 56))
    y = 30
    for line in lines:
        draw.text((34, y), line, font=bold, fill="white")
        y += 40
    draw.text((34, y + 3), "Zielspezifische Offline-Vorschau", font=regular, fill=(220, 241, 245))
    return image


def resolve(place: Place) -> tuple[Place, Candidate | None, Image.Image]:
    candidates = ranked_candidates(place)
    for candidate in candidates[:8]:
        try:
            return place, candidate, download_photo(candidate)
        except Exception as exc:
            print(f"WARN {place.title}: image {exc}", flush=True)
    return place, None, fallback(place)


def main() -> int:
    places = parse_places()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    resolved: dict[str, tuple[Candidate | None, Image.Image]] = {}
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(resolve, place): place for place in places}
        for number, future in enumerate(as_completed(futures), 1):
            place, candidate, image = future.result()
            resolved[place.title] = (candidate, image)
            print(f"{number:02d}/93 {'PHOTO' if candidate else 'ART'} {place.title}", flush=True)

    manifest = {"version": 1, "cellWidth": CELL[0], "cellHeight": CELL[1], "columns": COLUMNS, "places": {}}
    for region in REGION_COLOURS:
        region_places = [place for place in places if place.region == region]
        rows = math.ceil(len(region_places) / COLUMNS)
        sprite = Image.new("RGB", (CELL[0] * COLUMNS, CELL[1] * rows), (230, 235, 240))
        for index, place in enumerate(region_places):
            candidate, image = resolved[place.title]
            sprite.paste(image, ((index % COLUMNS) * CELL[0], (index // COLUMNS) * CELL[1]))
            manifest["places"][place.title] = {
                "region": region,
                "index": index,
                "photo": candidate is not None,
                "source": candidate.page if candidate else "",
                "author": candidate.author if candidate else "ReisePilot",
                "license": candidate.licence if candidate else "lokale Illustration",
                "licenseUrl": candidate.licence_url if candidate else "",
            }
        sprite.save(OUTPUT / f"{region.lower()}.webp", "WEBP", quality=80, method=6)
    (OUTPUT / "index.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    photos = sum(1 for candidate, _ in resolved.values() if candidate)
    print(f"DONE: {photos}/93 exact Wikimedia photos, {93 - photos} labelled illustrations", flush=True)
    return 0 if photos >= 75 else 1


if __name__ == "__main__":
    sys.exit(main())
