"""Measured sizes after import, which the download screen shows for catalog entries.

python -I scripts/catalog/installed_sizes.py <du listing> <dictionaries.db> [catalog] [out]

Both inputs come from the debug build on a device or emulator with the dictionaries installed:
  adb shell "run-as com.vpr.screenlate.debug sh -c 'cd files/dictionaries && du -sk *'" > du.txt
  adb exec-out run-as com.vpr.screenlate.debug cat databases/dictionaries.db > dictionaries.db
(and the same for dictionaries.db-wal and dictionaries.db-shm). Entries not installed there keep the size the out
file already has; entries whose catalog record gives an installedSize are left out.
"""
import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CATALOG = os.path.join(ROOT, "dictionary/api/src/main/assets/catalog/dictionaries-v2.json")
OUT = os.path.join(ROOT, "dictionary/api/src/main/assets/catalog/installed-sizes.json")
MIB = 1024 * 1024
FORMS_ONLY_SUFFIX = " (forms)"


def read_listing(lines):
    """Bytes per dictionary directory from `du -sk` lines."""
    sizes = {}
    for line in lines:
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and parts[0].isdigit():
            sizes[parts[1].rstrip("/").split("/")[-1]] = int(parts[0]) * 1024
    return sizes


def matches(entry, kind, index_url, title):
    """CatalogEntry.matches: the same kind, then the index URL or one of the title rules."""
    if entry.get("kind") != kind or title.endswith(FORMS_ONLY_SUFFIX) != (entry.get("build") == "forms"):
        return False
    if entry.get("indexUrl") and index_url == entry["indexUrl"]:
        return True
    if entry.get("installedTitle") and title.startswith(entry["installedTitle"]):
        return True
    if any(title.startswith(t) for t in entry.get("formerTitles", [])):
        return True
    return any(title == t or title.startswith(t + " [") for t in entry.get("oldTitles", []))


def download_bytes(entry):
    return entry.get("downloadSize") or entry.get("sizeMb", 0) * MIB


def measure(entries, dictionaries, listing):
    """Sizes per entry id from installed (id, title, kind, directory, indexUrl) rows; the newest copy counts."""
    sizes = {}
    for _, title, kind, directory, index_url in sorted(dictionaries):
        installed = listing.get(directory)
        if installed is None:
            continue
        for entry in entries:
            if "installedSize" in entry or not download_bytes(entry) or not matches(entry, kind, index_url, title):
                continue
            sizes[entry["id"]] = {"download": download_bytes(entry), "installed": installed}
    return sizes


def render(sizes):
    """One entry per line, sorted by id."""
    lines = [f'    {json.dumps(key)}: {{"download": {value["download"]}, "installed": {value["installed"]}}}'
             for key, value in sorted(sizes.items())]
    return '{\n  "sizes": {\n' + ",\n".join(lines) + "\n  }\n}\n"


def main(args):
    listing_path, db_path = args[0], args[1]
    catalog_path = args[2] if len(args) > 2 else CATALOG
    out_path = args[3] if len(args) > 3 else OUT
    with open(listing_path, encoding="utf-8") as listing_file:
        listing = read_listing(listing_file)
    with open(catalog_path, encoding="utf-8") as catalog_file:
        entries = json.load(catalog_file)["dictionaries"]
    db = sqlite3.connect(db_path)
    rows = db.execute("select id, title, kind, directory, indexUrl from dictionaries").fetchall()
    previous = {}
    if os.path.exists(out_path):
        with open(out_path, encoding="utf-8") as out_file:
            previous = json.load(out_file)["sizes"]
    ids = {entry["id"] for entry in entries if "installedSize" not in entry}
    sizes = {key: value for key, value in previous.items() if key in ids}
    measured = measure(entries, rows, listing)
    sizes.update(measured)
    with open(out_path, "w", encoding="utf-8", newline="\n") as out_file:
        out_file.write(render(sizes))
    print(f"{len(measured)} entries measured, {len(sizes)} in {out_path}")


if __name__ == "__main__":
    main(sys.argv[1:])
