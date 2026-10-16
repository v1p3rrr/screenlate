"""Writes the catalog entries of the frequency dictionaries that the app builds from wordfreq's word lists.

    python scripts/frequency/wordfreq_catalog.py [--cache testdata/wordfreq]

Each entry points at one word list in wordfreq's repository at a pinned commit (the large list where wordfreq has one,
else the small one) with its size and SHA-256; the app downloads it, checks it and builds the dictionary as
WordfreqDictionary does (the same conversion as wordfreq_dictionary.py). Entries of the template "wordfreq" in
dictionaries-v2.json are replaced; the template texts are kept as they are in the file.
"""
import argparse
import hashlib
import json
import os
import urllib.error
import urllib.request

COMMIT = "912caf64b657478d1dff1138efdc078947d54bb1"
VERSION = "3.2"
RAW = f"https://raw.githubusercontent.com/rspeer/wordfreq/{COMMIT}/wordfreq/data"
HOMEPAGE = "https://github.com/rspeer/wordfreq"
TEMPLATE = "wordfreq"

# The app's source languages except Japanese, which bundles its own frequency dictionary.
LANGUAGES = ["en", "zh", "ko", "es", "fr", "de", "it", "pt", "ru", "pl", "tr", "vi", "lv"]

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CATALOG = os.path.join(ROOT, "dictionary", "api", "src", "main", "assets", "catalog", "dictionaries-v2.json")


def fetch(name, cache):
    """The word list [name] from the cache, downloaded into it first when missing; None when wordfreq has none."""
    path = os.path.join(cache, name)
    if not os.path.exists(path):
        try:
            with urllib.request.urlopen(f"{RAW}/{name}") as response:
                data = response.read()
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise
        os.makedirs(cache, exist_ok=True)
        with open(path, "wb") as file:
            file.write(data)
    with open(path, "rb") as file:
        return file.read()


def entry(language, name, data):
    return {
        "id": f"wordfreq-{language}",
        "title": f"wordfreq ({language})",
        "template": TEMPLATE,
        "oldTitles": [f"wordfreq {language}"],
        "kind": "FREQUENCY",
        "category": "FREQUENCY",
        "sourceLanguage": language,
        "recommended": True,
        "downloadUrl": f"{RAW}/{name}",
        "downloadSize": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "build": "wordfreq",
        "version": VERSION,
        "license": "CC BY-SA 4.0",
        "homepage": HOMEPAGE,
    }


def entries(cache):
    result = []
    for language in LANGUAGES:
        for name in (f"large_{language}.msgpack.gz", f"small_{language}.msgpack.gz"):
            data = fetch(name, cache)
            if data is not None:
                result.append(entry(language, name, data))
                break
        else:
            print(f"wordfreq has no word list for {language}")
    return result


def replace(document, built):
    """[document] with its wordfreq entries replaced by [built], placed where the first one was or before the generated ones."""
    dictionaries = document["dictionaries"]
    positions = [i for i, e in enumerate(dictionaries) if e.get("template") == TEMPLATE]
    if positions:
        at = positions[0]
    else:
        at = next((i for i, e in enumerate(dictionaries) if str(e.get("template", "")).startswith("wiktionary")),
                  len(dictionaries))
    kept = [e for e in dictionaries if e.get("template") != TEMPLATE]
    at -= sum(1 for i in positions if i < at)
    return {**document, "dictionaries": kept[:at] + built + kept[at:]}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cache", default=os.path.join(ROOT, "testdata", "wordfreq"),
                        help="where downloaded word lists are kept")
    args = parser.parse_args()
    with open(CATALOG, encoding="utf-8") as file:
        document = json.load(file)
    if TEMPLATE not in document.get("templates", {}):
        raise SystemExit(f"The catalog has no template {TEMPLATE!r}")
    built = entries(args.cache)
    with open(CATALOG, "w", encoding="utf-8", newline="\n") as file:
        file.write(json.dumps(replace(document, built), ensure_ascii=False, indent=2) + "\n")
    print(f"{len(built)} wordfreq entries")


if __name__ == "__main__":
    main()
