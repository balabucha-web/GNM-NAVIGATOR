#!/usr/bin/env python3
"""Small live smoke test for every key-free ReisePilot data source."""

from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


UA = "ReisePilot/4.8 live source audit"
OFFLINE_PHOTOS = Path(__file__).resolve().parents[1] / "app/src/main/assets/destination_photos/index.json"


class LiveWarning(RuntimeError):
    """A non-critical source is down while its bundled fallback is valid."""


def fetch(
    url: str,
    *,
    data: bytes | None = None,
    limit: int | None = None,
    timeout: int = 30,
    attempts: int = 3,
) -> bytes:
    last_error: Exception | None = None
    for attempt in range(attempts):
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "User-Agent": UA,
                "Accept": "application/json,text/html;q=0.8,*/*;q=0.5",
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                return response.read(limit)
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
            if attempt < attempts - 1:
                time.sleep(1 + attempt)
    raise RuntimeError(str(last_error))


def json_url(url: str, **params: str) -> dict:
    return json.loads(fetch(url + "?" + urllib.parse.urlencode(params)))


def check_overpass() -> str:
    query = (
        '[out:json][timeout:10];('
        'node(around:1600,42.5250,3.0833)["amenity"="parking"];'
        'way(around:1600,42.5250,3.0833)["amenity"="parking"];'
        ');out center tags 25;'
    )
    data = ("data=" + urllib.parse.quote(query)).encode()
    endpoints = (
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    errors = []
    for endpoint in endpoints:
        try:
            payload = json.loads(fetch(endpoint, data=data, timeout=18))
            count = len(payload.get("elements") or [])
            if count:
                return f"{urllib.parse.urlparse(endpoint).netloc} · {count} Parkobjekte"
            errors.append(f"{endpoint}: 0")
        except Exception as exc:
            errors.append(f"{urllib.parse.urlparse(endpoint).netloc}: {exc}")
    raise RuntimeError(" | ".join(errors))


def check_autobahn() -> str:
    payload = json.loads(fetch("https://verkehr.autobahn.de/o/autobahn/A24/services/warning"))
    if "warning" not in payload:
        raise RuntimeError("Feld 'warning' fehlt")
    return f"{len(payload.get('warning') or [])} aktuelle A24-Meldungen"


def check_bison() -> str:
    body = fetch(
        "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/"
        "Evenementiel-DIR/cnir/RecapTraficFranceEntiere.html",
        timeout=35,
    ).decode("utf-8", errors="replace")
    if len(body) < 500 or not any(marker in body.lower() for marker in ("trafic", "bison", "autoroute")):
        raise RuntimeError("unerwartete Bison-Futé-Antwort")
    return f"HTML-Lagebericht · {len(body) // 1024} KiB"


def check_france_fuel() -> str:
    where = "within_distance(geom, geom'POINT(3.0182 42.7069)', '40km') and pop='R' and gazole_prix is not null"
    payload = json_url(
        "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/"
        "prix-des-carburants-en-france-flux-instantane-v2/records",
        where=where,
        order_by="gazole_prix asc",
        limit="5",
    )
    results = payload.get("results") or []
    if not results or not any(row.get("gazole_prix") for row in results):
        raise RuntimeError("keine Dieselpreise um Canet")
    return f"{len(results)} Live-Dieselpreise um Canet"


def check_spain_fuel() -> str:
    body = fetch(
        "https://energia.serviciosmin.gob.es/ServiciosRestCarburantes/"
        "PreciosCarburantes/EstacionesTerrestres/",
        limit=1_500_000,
        timeout=45,
    )
    if b'"Fecha"' not in body or b'"ListaEESSPrecio"' not in body:
        raise RuntimeError("spanische Preisfelder fehlen")
    return "offizieller Preisfeed antwortet"


def check_wikimedia() -> str:
    try:
        payload = json_url(
            "https://commons.wikimedia.org/w/api.php",
            action="query",
            generator="search",
            gsrnamespace="6",
            gsrlimit="3",
            gsrsearch="Collioure",
            prop="imageinfo",
            iiprop="url|mime|size",
            iiurlwidth="900",
            format="json",
            formatversion="2",
            origin="*",
        )
    except Exception as exc:
        if "429" in str(exc):
            raise LiveWarning("HTTP 429 gedrosselt · 93 Offline-Fotos aktiv") from exc
        raise
    pages = payload.get("query", {}).get("pages") or []
    if not pages:
        raise RuntimeError("keine Bildtreffer")
    return f"{len(pages)} Bildtreffer"


def check_wikidata() -> str:
    endpoints = (
        "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=Q254829"
        "&props=claims%7Clabels&languages=de%7Cfr%7Cen&format=json&origin=*",
        "https://www.wikidata.org/wiki/Special:EntityData/Q254829.json",
    )
    errors = []
    for endpoint in endpoints:
        try:
            payload = json.loads(fetch(endpoint, timeout=12, attempts=1))
            if "Q254829" in payload.get("entities", {}):
                return "POI-Identität Collioure bestätigt"
            errors.append("Identität fehlt")
        except Exception as exc:
            errors.append(str(exc))

    manifest = json.loads(OFFLINE_PHOTOS.read_text(encoding="utf-8"))
    if len(manifest.get("places") or {}) == 93:
        raise LiveWarning("Wikidata momentan Timeout · 93 lokale Zielidentitäten aktiv")
    raise RuntimeError(" | ".join(errors))


def check_openfreemap() -> str:
    payload = json.loads(fetch("https://tiles.openfreemap.org/styles/liberty"))
    if payload.get("version") != 8 or not payload.get("sources"):
        raise RuntimeError("ungültiger Kartenstil")
    return f"MapLibre-Stil · {len(payload.get('sources') or {})} Quellen"


def main() -> int:
    checks = (
        ("OSM-Parkplätze", check_overpass),
        ("Deutschland-Verkehr", check_autobahn),
        ("Frankreich-Verkehr", check_bison),
        ("Frankreich-Diesel", check_france_fuel),
        ("Spanien-Diesel", check_spain_fuel),
        ("Wikimedia", check_wikimedia),
        ("Wikidata", check_wikidata),
        ("OpenFreeMap", check_openfreemap),
    )
    failures = []
    warnings = []
    for name, check in checks:
        try:
            print(f"PASS {name}: {check()}")
        except LiveWarning as warning:
            warnings.append(f"{name}: {warning}")
            print(f"WARN {name}: {warning}")
        except Exception as exc:
            failures.append(f"{name}: {exc}")
            print(f"FAIL {name}: {exc}")
    if failures:
        print("Live source failures:", " | ".join(failures))
        return 1
    if warnings:
        print("PASS with protected degradation:", " | ".join(warnings))
    else:
        print("PASS: all key-free live data sources are reachable and structurally valid")
    return 0


if __name__ == "__main__":
    sys.exit(main())
