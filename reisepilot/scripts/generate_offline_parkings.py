#!/usr/bin/env python3
"""Create a small, dated OSM parking fallback for every destination."""

from __future__ import annotations

import hashlib
import json
import math
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/balabucha/reisepilot/DiscoverData.kt"
OUTPUT = ROOT / "app/src/main/assets/destination_parkings.json"
CACHE = Path("/tmp/reisepilot-parking-cache")
ENDPOINTS = (
    "https://overpass-api.de/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)
USER_AGENT = "ReisePilot/4.9 offline parking fallback builder"


@dataclass(frozen=True)
class Place:
    region: str
    title: str
    lat: float
    lon: float
    kind: str


def places() -> list[Place]:
    text = SOURCE.read_text(encoding="utf-8")
    pattern = re.compile(
        r'TravelPlace\(TravelRegion\.(\w+),\s*"([^"]+)",.*?'
        r'GeoPoint\(([-\d.]+),\s*([-\d.]+)\),\s*PlaceKind\.(\w+)',
        re.S,
    )
    result = [Place(region, title, float(lat), float(lon), kind) for region, title, lat, lon, kind in pattern.findall(text)]
    if len(result) != 93:
        raise RuntimeError(f"Expected 93 places, got {len(result)}")
    return result


def distance_m(a: tuple[float, float], b: tuple[float, float]) -> int:
    from math import asin, cos, radians, sin, sqrt
    lat1, lon1 = map(radians, a)
    lat2, lon2 = map(radians, b)
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    return round(12742000 * asin(sqrt(h)))


def radius(place: Place) -> int:
    if place.region in {"BARCELONA", "PARIS"}:
        return 1400
    if place.kind == "NATURE":
        return 2800
    return 2000


def request(endpoint: str, query: str) -> dict:
    cache_key = hashlib.sha256((endpoint + query).encode()).hexdigest()
    cache_file = CACHE / cache_key
    if cache_file.is_file():
        return json.loads(cache_file.read_text(encoding="utf-8"))
    body = ("data=" + urllib.parse.quote(query)).encode()
    req = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "User-Agent": USER_AGENT,
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=18) as response:
        payload = json.load(response)
    remark = str(payload.get("remark") or "")
    if any(word in remark.lower() for word in ("timed out", "runtime error", "dispatcher")):
        raise RuntimeError(remark[:120])
    if not isinstance(payload.get("elements"), list):
        raise RuntimeError("missing elements")
    CACHE.mkdir(parents=True, exist_ok=True)
    cache_file.write_text(json.dumps(payload), encoding="utf-8")
    return payload


def query(place: Place, distance: int) -> dict:
    overpass = (
        f'[out:json][timeout:10][maxsize:8388608];('
        f'node(around:{distance},{place.lat},{place.lon})["amenity"="parking"];'
        f'way(around:{distance},{place.lat},{place.lon})["amenity"="parking"];'
        f');out center tags 80;'
    )
    errors = []
    for endpoint in ENDPOINTS:
        try:
            return request(endpoint, overpass)
        except Exception as exc:
            errors.append(f"{urllib.parse.urlparse(endpoint).netloc}: {exc}")
    raise RuntimeError("; ".join(errors))


def parse(place: Place, payload: dict, maximum: int) -> list[dict]:
    result = []
    for element in payload.get("elements", []):
        tags = element.get("tags") or {}
        access = str(tags.get("access") or "").lower()
        if access in {"private", "no"} or str(tags.get("motor_vehicle") or "").lower() == "no":
            continue
        parking = str(tags.get("parking") or "").lower()
        if parking in {"lane", "street_side", "on_kerb", "half_on_kerb", "shoulder"}:
            continue
        center = element.get("center") or {}
        lat = element.get("lat", center.get("lat"))
        lon = element.get("lon", center.get("lon"))
        if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
            continue
        distance = distance_m((place.lat, place.lon), (float(lat), float(lon)))
        if distance > maximum + 300:
            continue
        operator = str(tags.get("operator") or "").strip()
        name = str(tags.get("name") or "").strip() or operator
        if access in {"customers", "customer"} and place.kind != "SHOPPING":
            continue
        kind = {
            "underground": "UNDERGROUND",
            "multi-storey": "MULTI_STOREY",
            "multistorey": "MULTI_STOREY",
            "surface": "SURFACE",
        }.get(parking, "UNKNOWN")
        if str(tags.get("park_ride") or "").lower() in {"yes", "train", "bus", "subway"}:
            kind = "PARK_RIDE"
        fee = {"yes": "PAID", "no": "FREE"}.get(str(tags.get("fee") or "").lower(), "UNKNOWN")
        supervised_raw = str(tags.get("supervised") or "").lower()
        supervised = True if supervised_raw == "yes" else False if supervised_raw == "no" else None
        score = 240 - distance / 11
        if name:
            score += 30
        if kind in {"UNDERGROUND", "MULTI_STOREY"} and place.region in {"BARCELONA", "PARIS"}:
            score += 65
        if supervised:
            score += 45
        if tags.get("capacity"):
            score += 10
        result.append(
            {
                "id": f"osm:{element.get('type', 'osm')}:{element.get('id', 0)}",
                "name": name or {"UNDERGROUND": "Tiefgarage", "MULTI_STOREY": "Parkhaus"}.get(kind, "Parkplatz"),
                "lat": float(lat),
                "lon": float(lon),
                "kind": kind,
                "distance": distance,
                "fee": fee,
                "capacity": int(tags["capacity"]) if str(tags.get("capacity") or "").isdigit() else None,
                "openingHours": str(tags.get("opening_hours") or ""),
                "access": access,
                "operator": operator,
                "maxHeight": str(tags.get("maxheight") or ""),
                "supervised": supervised,
                "score": score,
            }
        )
    seen, ranked = set(), []
    for row in sorted(result, key=lambda item: item["score"], reverse=True):
        key = (round(row["lat"], 4), round(row["lon"], 4), row["name"].lower())
        if key in seen:
            continue
        seen.add(key)
        row.pop("score", None)
        ranked.append(row)
    return ranked[:3]


def resolve(place: Place) -> tuple[Place, list[dict]]:
    first_radius = radius(place)
    for search_radius in (first_radius, min(4200, first_radius + 1400)):
        try:
            parkings = parse(place, query(place, search_radius), search_radius)
            if parkings:
                return place, parkings
        except Exception as exc:
            print(f"WARN {place.title} ({search_radius} m): {exc}", flush=True)
    return place, []


def main() -> int:
    all_places = places()
    values: dict[str, list[dict]] = {}
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {executor.submit(resolve, place): place for place in all_places}
        for number, future in enumerate(as_completed(futures), 1):
            place, parkings = future.result()
            values[f"{place.region}:{place.title}"] = parkings
            print(f"{number:02d}/93 {len(parkings)} PARK {place.title}", flush=True)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(
            {"version": 1, "generatedAt": "2026-07-22", "places": values},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    covered = sum(bool(rows) for rows in values.values())
    print(f"DONE: {covered}/93 destinations have an offline OSM parking fallback", flush=True)
    return 0 if covered == len(all_places) else 1


if __name__ == "__main__":
    sys.exit(main())
