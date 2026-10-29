"""Lists the letters each downloadable font has in the font catalog, for the per-language letter check.

    python scripts/fonts/font_letters.py overlay/src/main/assets/fonts/catalog.json [--cache <directory>] [--write]

For every font offered by its subsets (one without "languages"), the files are downloaded (or read from --cache, where
they are kept by file name) and the code points below U+2000 that are letters or marks (Unicode categories L and M) in
all of them are written as hex ranges to the font's "letters" (`0041-005A,00AA,...`). The app offers such a font for a
language only when it has the language's letters (`LanguageSupport.fontLetters`). Without --write the script prints
what it would change. Needs fontTools (pip install fonttools).
"""
import argparse
import json
import os
import re
import sys
import unicodedata
import urllib.parse
import urllib.request

from fontTools.ttLib import TTFont

LIMIT = 0x2000


def font_file(url, cache):
    name = urllib.parse.unquote(url.rsplit("/", 1)[-1])
    if cache:
        path = os.path.join(cache, name)
        if not os.path.exists(path):
            os.makedirs(cache, exist_ok=True)
            urllib.request.urlretrieve(url, path)
        return path
    path, _ = urllib.request.urlretrieve(url)
    return path


def letters_of(paths):
    common = None
    for path in paths:
        font = TTFont(path, lazy=True)
        points = {p for p in font.getBestCmap() if p < LIMIT and unicodedata.category(chr(p))[0] in "LM"}
        font.close()
        common = points if common is None else common & points
    return common or set()


def ranges(points):
    out = []
    for point in sorted(points):
        if out and point == out[-1][1] + 1:
            out[-1][1] = point
        else:
            out.append([point, point])
    return ",".join(f"{a:04X}" if a == b else f"{a:04X}-{b:04X}" for a, b in out)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("catalog")
    parser.add_argument("--cache", help="a directory the font files are kept in by name")
    parser.add_argument("--write", action="store_true", help="write the letters into the catalog")
    args = parser.parse_args(argv)
    with open(args.catalog, encoding="utf-8") as file:
        text = file.read()
    for font in json.loads(text)["fonts"]:
        if font.get("languages") or not font.get("subsets"):
            continue
        listed = ranges(letters_of([font_file(f["url"], args.cache) for f in font["files"]]))
        print(f"{font['id']}: {len(listed)} characters of ranges")
        # The line after the font's subsets: its letters, replaced or added, so the file keeps its layout.
        block = re.compile(
            r'(\n( *)"id": "' + re.escape(font["id"]) + r'",\n(?:\2.*\n)*?\2"subsets": \[[^\]]*\],\n)(\2"letters": "[^"]*",\n)?'
        )
        match = block.search(text)
        if not match:
            raise SystemExit(f"{font['id']}: no subsets line")
        line = f'{match.group(2)}"letters": "{listed}",\n'
        text = text[:match.start()] + match.group(1) + line + text[match.end():]
    if args.write:
        with open(args.catalog, "w", encoding="utf-8", newline="\n") as file:
            file.write(text)


if __name__ == "__main__":
    main(sys.argv[1:])
