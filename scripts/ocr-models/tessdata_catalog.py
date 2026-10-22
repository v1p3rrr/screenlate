"""Writes the catalog entries of Tesseract's recognition models (tessdata_fast and tessdata_best).

    python -I scripts/ocr-models/tessdata_catalog.py [--cache testdata/tessdata]

Each entry points at one traineddata file in a tesseract-ocr repository at a pinned commit, with its size and SHA-256;
the app downloads it unchanged and keeps it as tessdata/<code>.traineddata, the layout Tesseract reads. Entries of the
templates "tesseract-fast" and "tesseract-best" in dictionaries-v2.json are replaced; the template texts are kept as
they are in the file. The version is the start of the file's SHA-256, so moving a pin offers an update only for the
files that changed.
"""
import argparse
import hashlib
import json
import os
import urllib.request

REPOSITORIES = {
    "fast": ("tessdata_fast", "87416418657359cb625c412a48b6e1d6d41c29bd"),
    "best": ("tessdata_best", "e12c65a915945e4c28e237a9b52bc4a8f39a0cec"),
}

# The app's languages whose script Tesseract reads well enough to offer it, with Tesseract's code for each.
LANGUAGES = {
    "en": "eng", "de": "deu", "fr": "fra", "es": "spa", "it": "ita", "pt": "por",
    "pl": "pol", "tr": "tur", "lv": "lav", "vi": "vie", "ru": "rus", "nl": "nld", "cs": "ces", "id": "ind",
    "ms": "msa", "tl": "fil", "uk": "ukr", "be": "bel", "el": "ell", "hi": "hin", "bn": "ben", "ka": "kat", "hy": "hye",
    "th": "tha", "km": "khm", "ar": "ara", "he": "heb", "kk": "kaz", "ky": "kir", "sv": "swe", "no": "nor", "da": "dan", "ro": "ron", "la": "lat",
    # Serbian's in Cyrillic, which its text is by default.
    "hr": "hrv", "sr": "srp",
}

# Recommended per language by the rule over the clean and hard screens (ai/notes/ocr-engines.md): the fast models of
# Georgian, Armenian, Thai, Khmer, Arabic and Hebrew, as best makes nearly as many errors and nothing else in the
# catalog reads them on the device (Paddle's Arabic model is not in it yet, Paddle has no Hebrew one); the other
# languages are read better by Paddle or ML Kit, and paddle_models.py marks theirs.
RECOMMENDED = {"ka": "fast", "hy": "fast", "th": "fast", "km": "fast", "ar": "fast", "he": "fast"}

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CATALOG = os.path.join(ROOT, "dictionary", "api", "src", "main", "assets", "catalog", "dictionaries-v2.json")


def template(variant):
    return f"tesseract-{variant}"


def url(variant, code):
    repository, commit = REPOSITORIES[variant]
    return f"https://raw.githubusercontent.com/tesseract-ocr/{repository}/{commit}/{code}.traineddata"


def fetch(variant, code, cache):
    """The traineddata file from the cache, downloaded into it first when missing."""
    repository, commit = REPOSITORIES[variant]
    path = os.path.join(cache, repository, commit, f"{code}.traineddata")
    if not os.path.exists(path):
        with urllib.request.urlopen(url(variant, code)) as response:
            data = response.read()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as file:
            file.write(data)
    with open(path, "rb") as file:
        return file.read()


def entry(language, code, variant, data):
    repository, _ = REPOSITORIES[variant]
    model = f"tesseract-{variant}-{code}"
    sha256 = hashlib.sha256(data).hexdigest()
    return {
        "id": model,
        "title": f"Tesseract {variant} ({code})",
        "template": template(variant),
        "category": "OCR_MODEL",
        "sourceLanguage": language,
        "recommended": RECOMMENDED.get(language) == variant,
        "downloadUrl": url(variant, code),
        "downloadSize": len(data),
        "sha256": sha256,
        "model": model,
        "engine": "tesseract",
        "file": f"tessdata/{code}.traineddata",
        "version": sha256[:12],
        "license": "Apache-2.0",
        "homepage": f"https://github.com/tesseract-ocr/{repository}",
    }


def entries(cache):
    return [
        entry(language, code, variant, fetch(variant, code, cache))
        for language, code in LANGUAGES.items()
        for variant in REPOSITORIES
    ]


def replace(document, built):
    """[document] with its Tesseract entries replaced by [built], placed where the first one was or at the end."""
    templates = {template(variant) for variant in REPOSITORIES}
    dictionaries = document["dictionaries"]
    positions = [i for i, e in enumerate(dictionaries) if e.get("template") in templates]
    kept = [e for e in dictionaries if e.get("template") not in templates]
    at = positions[0] if positions else len(dictionaries)
    at -= sum(1 for i in positions if i < at)
    return {**document, "dictionaries": kept[:at] + built + kept[at:]}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cache", default=os.path.join(ROOT, "testdata", "tessdata"),
                        help="where downloaded traineddata files are kept")
    args = parser.parse_args()
    with open(CATALOG, encoding="utf-8") as file:
        document = json.load(file)
    missing = [template(v) for v in REPOSITORIES if template(v) not in document.get("templates", {})]
    if missing:
        raise SystemExit(f"The catalog has no template {', '.join(missing)}")
    built = entries(args.cache)
    with open(CATALOG, "w", encoding="utf-8", newline="\n") as file:
        file.write(json.dumps(replace(document, built), ensure_ascii=False, indent=2) + "\n")
    print(f"{len(built)} Tesseract entries")


if __name__ == "__main__":
    main()
