"""Builds the Hanja character dictionary (Yomitan format) from the Unicode Han Database.

    python scripts/hanja/unihan_hanja.py <Unihan.zip> dictionary/api/src/main/assets/catalog/unihan-hanja.zip

Unihan.zip is https://www.unicode.org/Public/<version>/ucd/Unihan.zip. Every character with a Korean reading
(kHangul) becomes an entry: the readings in Hangul, those of the 2007 education list first, the English meanings
(kDefinition, split at semicolons), the stroke count, and tags for the education (kKoreanEducationHanja) and personal
name (kKoreanName) lists. The archive is reproducible: same input, same bytes. Unihan is under the Unicode License v3
(see NOTICE).
"""
import json
import re
import sys
import zipfile

TITLE = "Unihan Hanja"
EDUCATION = "교육용"
NAMES = "인명용"
BANK_SIZE = 2000
FIELDS = {"kHangul", "kDefinition", "kTotalStrokes", "kKoreanEducationHanja", "kKoreanName"}


def read_unihan(path):
    """Fields of [FIELDS] by character, and the Unicode version the files name."""
    characters = {}
    version = None
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            for line in archive.read(name).decode("utf-8").splitlines():
                if line.startswith("#"):
                    found = re.match(r"# Unicode Version (\S+)", line)
                    if found:
                        version = found.group(1)
                    continue
                if not line.strip():
                    continue
                code, field, value = line.split("\t", 2)
                if field in FIELDS:
                    characters.setdefault(chr(int(code[2:], 16)), {})[field] = value
    if version is None:
        raise SystemExit("no Unicode version in the Unihan files")
    return characters, version


def readings(value):
    """Hangul readings from kHangul ("락:0E 낙:0"), those on the 2007 education list (source E) first."""
    parsed = [item.split(":", 1) for item in value.split()]
    education = [reading for reading, sources in parsed if "E" in sources]
    return education + [reading for reading, sources in parsed if "E" not in sources]


def entry(character, fields):
    meanings = [m.strip() for m in fields.get("kDefinition", "").split(";") if m.strip()]
    tags = [tag for tag, field in ((EDUCATION, "kKoreanEducationHanja"), (NAMES, "kKoreanName")) if field in fields]
    stats = {}
    if "kTotalStrokes" in fields:
        stats["strokes"] = fields["kTotalStrokes"].split()[0]
    return [character, " ".join(readings(fields["kHangul"])), "", " ".join(tags), meanings, stats]


def build(characters, version):
    """The archive's files, name to bytes."""
    rows = [entry(c, f) for c, f in sorted(characters.items(), key=lambda item: ord(item[0])) if "kHangul" in f]
    index = {
        "title": TITLE,
        "format": 3,
        "revision": f"unihan-{version}",
        "sequenced": False,
        "author": "Screenlate",
        "url": "https://www.unicode.org/charts/unihan.html",
        "description": f"Korean readings and English meanings of {len(rows)} hanja from the Unicode Han Database "
                       f"(Unihan), Unicode {version}.",
        "attribution": "Unicode Han Database (Unihan), © Unicode, Inc., under the Unicode License v3.",
        "sourceLanguage": "ko",
        "targetLanguage": "en",
    }
    tags = [
        [EDUCATION, "frequent", -5, "On the list of basic hanja for education (2007)", 0],
        [NAMES, "frequent", -5, "On the list of hanja for personal names", 0],
        ["strokes", "misc", 0, "Stroke count", 0],
    ]
    files = {"index.json": index, "tag_bank_1.json": tags}
    for start in range(0, len(rows), BANK_SIZE):
        files[f"kanji_bank_{start // BANK_SIZE + 1}.json"] = rows[start:start + BANK_SIZE]
    return {name: json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            for name, value in files.items()}, len(rows)


def write(files, path):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in files.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, data, compresslevel=9)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    characters, version = read_unihan(sys.argv[1])
    files, count = build(characters, version)
    write(files, sys.argv[2])
    print(f"{count} hanja from Unicode {version}")


if __name__ == "__main__":
    main()
