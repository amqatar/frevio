#!/usr/bin/env python3
"""Fetch poster and backdrop images of Blender open movies from Wikimedia Commons for the
UI prototype, keeping only freely licensed files and recording each file's credit.

Writes into prototype/src/desktopMain/resources/art/:
  <id>-poster.jpg     portrait image (taller than wide)
  <id>-backdrop.jpg   landscape image (wider than tall)
  credits.txt         one line per file: id<TAB>title<TAB>author<TAB>license<TAB>source page

Only licences CC BY, CC BY-SA or public domain are accepted, read from Commons metadata, not
assumed. Anything not found is skipped; the app falls back to a coloured placeholder.
Run in CI before building the prototype (this needs internet access to commons.wikimedia.org).
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "MarqueePrototypeBuild/1.0 (open-movie artwork fetch for a UI mockup)"
OUT = os.path.join(os.path.dirname(__file__), "..", "prototype", "src", "desktopMain", "resources", "art")

# id, search terms. The ids match the prototype's mock catalog.
FILMS = [
    ("bbb", "Big Buck Bunny"),
    ("sintel", "Sintel"),
    ("tos", "Tears of Steel"),
    ("ed", "Elephants Dream"),
    ("cosmos", "Cosmos Laundromat"),
    ("spring", "Spring Blender Animation Studio"),
    ("agent327", "Agent 327 Operation Barbershop"),
    ("sprite", "Sprite Fright"),
    ("coffee", "Coffee Run Blender"),
    ("hero", "Hero Blender Grease Pencil"),
    ("wingit", "Wing It Blender"),
    ("caminandes", "Caminandes"),
]

FREE = re.compile(r"^(?:cc[- ]?by(?:-sa)?(?:[- ][0-9.]+)?|public domain|cc0(?: 1\.0)?)$", re.I)
TAGS = re.compile(r"<[^>]+>")


def api(params):
    q = urllib.parse.urlencode({**params, "format": "json", "formatversion": "2"})
    req = urllib.request.Request(f"{API}?{q}", headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def candidates(term, width):
    data = api({
        "action": "query", "generator": "search", "gsrnamespace": "6",
        "gsrsearch": f"{term} filetype:bitmap", "gsrlimit": "25",
        "prop": "imageinfo", "iiprop": "url|size|extmetadata|mime", "iiurlwidth": str(width),
    })
    for page in data.get("query", {}).get("pages", []):
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata", {})
        lic = TAGS.sub("", meta.get("LicenseShortName", {}).get("value", "")).strip()
        if not FREE.match(lic) or info.get("mime") not in ("image/jpeg", "image/png"):
            continue
        title = page.get("title", "")
        # Keep files whose name mentions the film, to avoid unrelated search hits.
        key = term.split()[0].lower()
        if key not in title.lower():
            continue
        yield {
            "title": title,
            "w": info.get("width", 0), "h": info.get("height", 0),
            "thumb": info.get("thumburl") or info.get("url"),
            "page": info.get("descriptionurl", ""),
            "license": lic,
            "author": TAGS.sub("", meta.get("Artist", {}).get("value", "Blender Foundation")).strip() or "Blender Foundation",
        }


def download(url, path):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r, open(path, "wb") as f:
        f.write(r.read())


def main():
    os.makedirs(OUT, exist_ok=True)
    credits = []
    for fid, term in FILMS:
        try:
            found = list(candidates(term, 1280))
        except Exception as e:  # network or API error: skip this film
            print(f"{fid}: search failed: {e}", file=sys.stderr)
            continue
        poster = next((c for c in found if c["h"] > c["w"] * 1.2), None)
        backdrop = next((c for c in found if c["w"] > c["h"] * 1.4), None)
        for kind, c in (("poster", poster), ("backdrop", backdrop)):
            if not c:
                print(f"{fid}: no free {kind} found")
                continue
            name = f"{fid}-{kind}.jpg"
            try:
                download(c["thumb"], os.path.join(OUT, name))
                credits.append("\t".join([fid, c["title"].removeprefix("File:"), c["author"], c["license"], c["page"]]))
                print(f"{fid}: {kind} <- {c['title']} ({c['license']})")
            except Exception as e:
                print(f"{fid}: {kind} download failed: {e}", file=sys.stderr)
            time.sleep(0.3)
    with open(os.path.join(OUT, "credits.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(credits) + "\n")
    print(f"{len(credits)} images saved to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
