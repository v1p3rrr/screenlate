#!/usr/bin/env python3
"""Converts PanLex expressions of two languages into a Yomitan dictionary archive.

    python scripts/panlex/panlex_yomitan.py <kir.tsv> <rus.tsv> <out.zip>

The inputs are two per-language files of the `cointegrated/panlex-meanings` mirror of the PanLex database (the
2024-03-01 dump, CC0): one row per expression and meaning. Expressions of the standard varieties (`kir-000`,
`rus-000`) that share a meaning translate each other. Each headword gets one definition, its translations in the
order of the meanings that support them, most first.

Left out, as a sample of the Kazakh and Kyrgyz files showed them to be mostly noise: headwords with letters outside
the language's alphabet (Kazakh words filed as Kyrgyz) or with Latin letters (codes, software names); time zones
(Малайзия уақыты, Иркутск жайкы убактысы, translated as the place alone) and words with a capital after a lowercase
letter (interface strings such as ЖыйынтыкДиаграм); meanings shared by many headwords (loose thesaurus groups: one
Russian word for sixteen Kyrgyz ones) or by many translations (lists: the cities of Russia under Russia);
translations in another script. Other capitalized headwords stay: places, names and terms (Оңтүстік-шығыс Азия,
Ысыккөл областы). Russian stress marks are kept in the shown spelling and ignored when translations are compared.
"""

import argparse
import collections
import csv
import json
import re
import sys
import zipfile
from pathlib import Path

DUMP = "2024-03-01"
MIRROR = "https://huggingface.co/datasets/cointegrated/panlex-meanings"
MIRROR_REVISION = "0422f0e60f39a5ed9306ee637555789fdc5b5837"

#: ISO 639-3 codes of the files the converter knows, as the app's language codes.
LANGUAGES = {"kaz": "kk", "kir": "ky", "eng": "en", "rus": "ru"}

RUSSIAN_LETTERS = set("абвгдеёжзийклмнопрстуфхцчшщъыьэюя")

#: The lowercase letters of each source language; a headword with any other letter, in either case, is left out.
ALPHABETS = {
    "kk": RUSSIAN_LETTERS | set("әғқңөұүһі"),
    "ky": RUSSIAN_LETTERS | set("ңөү"),
}

#: The word "time" of a time zone's name in each source language; a capitalized headword with it is left out.
TIME_ZONE_WORDS = {
    "kk": {"уақыт", "уақыты"},
    "ky": {"убакыт", "убакыты", "убактысы"},
}

STRESS_MARKS = "\u0301\u0300"

#: A meaning with more headwords than this groups words loosely rather than translating them.
MAX_HEADWORDS_PER_MEANING = 4

#: A meaning with more translations than this is a list (the cities of a country) rather than a translation.
MAX_TRANSLATIONS_PER_MEANING = 20

#: Translations shown per headword, the best supported first.
MAX_TRANSLATIONS = 10

TERMS_PER_BANK = 10_000


def read_rows(path):
    """`(text, meaning, variety)` of every row of a mirror file."""
    with open(path, encoding="utf-8", newline="") as stream:
        reader = csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)
        header = next(reader)
        columns = [header.index(name) for name in ("txt", "meaning", "langvar_uid")]
        for row in reader:
            if len(row) == len(header):
                yield tuple(row[column] for column in columns)


def plain(text):
    """[text] without stress marks, for comparing translations."""
    return "".join(c for c in text if c not in STRESS_MARKS)


def is_headword(text, language):
    """Whether [text] is a word or phrase written in [language]'s own letters, other than a time zone or a word with a
    capital after a lowercase letter."""
    letters = [c for c in text if c.isalpha()]
    alphabet = ALPHABETS[language]
    if not letters or not all(c.lower() in alphabet for c in letters):
        return False
    if any(a.islower() and b.isupper() for a, b in zip(text, text[1:])):
        return False
    words = set(re.findall(r"\w+", text.lower()))
    return not (any(c.isupper() for c in letters) and words & TIME_ZONE_WORDS[language])


def is_translation(text, language):
    """Whether [text] is written in [language]'s script: Russian in Russian letters, English in Latin ones."""
    letters = [c.lower() for c in plain(text) if c.isalpha()]
    if not letters:
        return False
    if language == "ru":
        return all(c in RUSSIAN_LETTERS for c in letters)
    return all(c < "\u0250" for c in letters)


def variety_of(path):
    """The standard variety (`kir-000`) and the language code of a mirror file named by its ISO 639-3 code."""
    code = Path(path).stem
    if code not in LANGUAGES:
        raise ValueError(f"{Path(path).name}: not one of {', '.join(f'{c}.tsv' for c in LANGUAGES)}")
    return f"{code}-000", LANGUAGES[code]


def read_meanings(path, variety, keep, among=None):
    """Meaning → expressions of [variety] in [path] for which [keep] holds, in file order; only the meanings [among]
    when given, as the English file has tens of millions of rows."""
    meanings = {}
    for text, meaning, uid in read_rows(path):
        if uid != variety or (among is not None and meaning not in among) or not keep(text):
            continue
        texts = meanings.setdefault(meaning, [])
        if text not in texts:
            texts.append(text)
    return meanings


def join(headwords, translations):
    """Headword → its translations, the ones more meanings support first, each spelled as shown.

    [headwords] and [translations] map meanings to expressions. A translation spelled with stress marks is shown so;
    translations that differ only in them count as one.
    """
    support = collections.defaultdict(collections.Counter)
    order = collections.defaultdict(dict)
    shown = {}
    for meaning, words in headwords.items():
        targets = translations.get(meaning)
        if not targets or len(words) > MAX_HEADWORDS_PER_MEANING:
            continue
        keys = list(dict.fromkeys(plain(t) for t in targets))
        if len(keys) > MAX_TRANSLATIONS_PER_MEANING:
            continue
        for target in targets:
            key = plain(target)
            if key not in shown or (target != key and shown[key] == key):
                shown[key] = target
        for word in words:
            for key in keys:
                support[word][key] += 1
                order[word].setdefault(key, len(order[word]))
    return {
        word: [shown[key] for key in sorted(counts, key=lambda k: (-counts[k], order[word][k]))][:MAX_TRANSLATIONS]
        for word, counts in support.items()
    }


def build(entries, source, target):
    """The files of the Yomitan archive, by name."""
    index = {
        "title": f"PanLex {source}-{target}",
        "format": 3,
        "revision": DUMP,
        "sequenced": True,
        "author": "PanLex",
        "url": "https://panlex.org/",
        "description": f"Translations from the PanLex database (the {DUMP} snapshot), each headword with the "
                       "translations of every source PanLex gathered, the most often given first. "
                       "Converted for Yomitan by Screenlate.",
        "attribution": f"PanLex database snapshot of {DUMP}, CC0 1.0 (https://panlex.org/license), as mirrored at "
                       f"{MIRROR} (revision {MIRROR_REVISION[:12]}).",
        "sourceLanguage": source,
        "targetLanguage": target,
    }
    terms = [
        [word, "", "", "", 0, [", ".join(translations)], sequence, ""]
        for sequence, (word, translations) in enumerate(sorted(entries.items()), start=1)
    ]
    files = {"index.json": index}
    for number, start in enumerate(range(0, len(terms), TERMS_PER_BANK), start=1):
        files[f"term_bank_{number}.json"] = terms[start:start + TERMS_PER_BANK]
    return files


def write_zip(files, out):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, value in files.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, json.dumps(value, ensure_ascii=False, separators=(",", ":")))


def convert(source_path, target_path):
    """The archive's files for the two mirror files."""
    source_variety, source = variety_of(source_path)
    target_variety, target = variety_of(target_path)
    if source not in ALPHABETS:
        raise ValueError(f"{Path(source_path).name}: no alphabet for {source}")
    headwords = read_meanings(source_path, source_variety, lambda text: is_headword(text, source))
    translations = read_meanings(target_path, target_variety, lambda text: is_translation(text, target), headwords)
    return build(join(headwords, translations), source, target)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", help="the mirror's file of the headwords' language (kaz.tsv, kir.tsv)")
    parser.add_argument("target", help="the mirror's file of the translations' language (eng.tsv, rus.tsv)")
    parser.add_argument("out", help="Yomitan archive to write")
    args = parser.parse_args(argv)
    files = convert(args.source, args.target)
    write_zip(files, args.out)
    terms = sum(len(rows) for name, rows in files.items() if name.startswith("term_bank_"))
    print(f"{files['index.json']['title']} {DUMP}: {terms} headwords, {Path(args.out).stat().st_size >> 10} KB")


if __name__ == "__main__":
    sys.exit(main())
