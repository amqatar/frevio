#!/usr/bin/env python3
"""Fetch a pinned set of Blender open-movie images from Wikimedia Commons for the UI prototype.

Each entry names an exact Commons file that was checked by eye to show the right film.
At build time the script re-reads each file's licence from Commons and keeps it only if it
is CC BY, CC BY-SA, CC0 or public domain, so a changed licence can't slip in.

Writes into prototype/src/desktopMain/resources/art/:
  <id>-poster.jpg / <id>-backdrop.jpg   (portrait / landscape)
  credits.txt   id<TAB>file<TAB>author<TAB>license<TAB>source page

Films without a pinned file keep the app's coloured placeholder. Needs internet access to
commons.wikimedia.org (CI has it). Rate limits (HTTP 429) are retried with backoff.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "MarqueePrototypeBuild/1.1 (open-movie artwork fetch for a UI mockup)"
OUT = os.path.join(os.path.dirname(__file__), "..", "prototype", "src", "desktopMain", "resources", "art")

# (app id, kind, exact Commons file name). Verified matches only; add more the same way.
PINNED = [
    ("bbb", "poster", "Big buck bunny poster big.jpg"),
    ("bbb", "backdrop", "Big.Buck.Bunny.-.Bunny.with.Bow.png"),
    ("sintel", "poster", "Sintel Cover Durian Project.jpg"),
    ("sintel", "backdrop", "Durian - Sintel-wallpaper-ishtar.jpg"),
    ("tos", "backdrop", "Tears of Steel frame 01 2a.jpg"),
    ("cosmos", "poster", "Cosmos Laundromat Victor Poster.jpg"),
    ("sprite", "poster", "Sprite Fright-concept art-sprite 01.jpg"),
    ("sprite", "backdrop", "Sprite Fright-concept art-Ellie 01.jpg"),
    ("coffee", "poster", "Coffee Run-movie poster.png"),
    ("coffee", "backdrop", "Coffee Run - screenshot-intro.png"),
]
WIDTH = {"poster": 600, "backdrop": 1280}

FREE = re.compile(r"^(?:cc[- ]?by(?:-sa)?(?:[- ][0-9.]+)?|public domain|cc0(?: 1\.0)?)$", re.I)
TAGS = re.compile(r"<[^>]+>")


def get(url, tries=5):
    delay = 2.0
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < tries - 1:
                wait = float(e.headers.get("Retry-After") or delay)
                time.sleep(wait)
                delay *= 2
                continue
            raise


def info(filename, width):
    q = urllib.parse.urlencode({
        "action": "query", "titles": f"File:{filename}", "prop": "imageinfo",
        "iiprop": "url|extmetadata", "iiurlwidth": str(width), "format": "json", "formatversion": "2",
    })
    page = json.loads(get(f"{API}?{q}"))["query"]["pages"][0]
    if page.get("missing"):
        return None
    ii = page["imageinfo"][0]
    meta = ii.get("extmetadata", {})
    return {
        "thumb": ii.get("thumburl") or ii["url"],
        "page": ii.get("descriptionurl", ""),
        "license": TAGS.sub("", meta.get("LicenseShortName", {}).get("value", "")).strip(),
        "author": TAGS.sub("", meta.get("Artist", {}).get("value", "")).strip() or "Blender Foundation",
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    credits = []
    for fid, kind, filename in PINNED:
        try:
            i = info(filename, WIDTH[kind])
            if i is None:
                print(f"{fid}: {kind}: {filename} not found on Commons")
                continue
            if not FREE.match(i["license"]):
                print(f"{fid}: {kind}: skipped, licence is '{i['license']}'")
                continue
            with open(os.path.join(OUT, f"{fid}-{kind}.jpg"), "wb") as f:
                f.write(get(i["thumb"]))
            credits.append("\t".join([fid, filename, i["author"], i["license"], i["page"]]))
            print(f"{fid}: {kind} <- {filename} ({i['license']})")
        except Exception as e:
            print(f"{fid}: {kind}: failed: {e}", file=sys.stderr)
        time.sleep(1.0)
    with open(os.path.join(OUT, "credits.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(credits) + ("\n" if credits else ""))
    print(f"{len(credits)} of {len(PINNED)} images saved to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
