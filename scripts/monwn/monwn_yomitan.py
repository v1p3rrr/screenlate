#!/usr/bin/env python3
"""Converts the Mongolian Wordnet (MonWN) into a Mongolian-English Yomitan dictionary archive.

    python scripts/monwn/monwn_yomitan.py <wn-data-mon.tsv> <WNdb-3.0.tar.gz> <out.zip>

MonWN links Mongolian lemmas to the synsets of Princeton WordNet 3.0 (the Open Multilingual Wordnet's tab-separated
format: synset, `mon:lemma` or `mon:def`, text). Each Mongolian headword gets one row per part of speech, the part of
speech as its tag (n, v, adj, adv, as Wiktionary's exports write them, so the lookup's endings reach the right rows),
and one definition per synset: the English lemmas of the synset, then MonWN's Mongolian definition where it has one.
Synsets keep the order of the MonWN file; synsets with the same English lemmas are shown once.

Headwords are cleaned as a sample of the file showed the need: spaces trimmed and collapsed; a note in slashes or
parentheses after the word (бутан /хий/) moves into the definition; Latin look-alikes inside Cyrillic words (тoс with
a Latin o) become Cyrillic. Left out: headwords with letters outside the Mongolian alphabet (single Latin letters,
mis-encoded text), and headwords with other punctuation left (two words joined by a comma or a slash, unclosed notes).
"""

import argparse
import hashlib
import io
import json
import re
import sys
import tarfile
import unicodedata
import zipfile
from pathlib import Path

MONWN_URL = "https://github.com/kbatsuren/monwn"
MONWN_COMMIT = "c747dcf3be8af169e717dc2ae6d2c15bc7735688"
MONWN_SHA256 = "a09f9b8cc897dea1d774c780499979cf8ba99f0f7f7766f61d1901c4cb84be62"
WORDNET_URL = "https://wordnetcode.princeton.edu/3.0/WNdb-3.0.tar.gz"
WORDNET_SHA256 = "658b1ba191f5f98c2e9bae3e25c186013158f30ef779f191d2a44e5d25046dc8"

MONGOLIAN_LETTERS = set("абвгдеёжзийклмноөпрстуүфхцчшщъыьэюя")

#: Latin letters that look like Cyrillic ones, as typed inside MonWN's Cyrillic headwords.
LOOK_ALIKES = str.maketrans("aeopcxy", "аеорсху")

#: WordNet's part of speech letter → the tag of the row, as Wiktionary's exports name them.
PARTS_OF_SPEECH = {"n": "n", "v": "v", "a": "adj", "s": "adj", "r": "adv"}

#: The tag bank: name, category, order, notes, score.
TAGS = [
    ["n", "partOfSpeech", 0, "noun", 0],
    ["v", "partOfSpeech", 0, "verb", 0],
    ["adj", "partOfSpeech", 0, "adjective", 0],
    ["adv", "partOfSpeech", 0, "adverb", 0],
]

#: WordNet's data files by the part of speech letter of MonWN's synset ids.
DATA_FILES = {"n": "data.noun", "v": "data.verb", "a": "data.adj", "r": "data.adv"}

#: A note after the headword: `/…/` or `(…)`, closed.
NOTE = re.compile(r"\s*(?:/([^/]+)/|\(([^()]+)\))\s*$")

TERMS_PER_BANK = 10_000


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read_monwn(stream):
    """`(lemmas, definitions)`: `(synset, text)` of every lemma row in file order, and synset → Mongolian definition."""
    lemmas = []
    definitions = {}
    for line in stream:
        if line.startswith("#"):
            continue
        parts = line.rstrip("\n").split("\t")
        if len(parts) != 3:
            continue
        synset, kind, text = parts
        if kind == "mon:lemma":
            lemmas.append((synset, text))
        elif kind.startswith("mon:def") and synset not in definitions:
            definitions[synset] = " ".join(text.split())
    return lemmas, definitions


def read_wordnet_lines(lines, pos):
    """Synset id (`00001740-a`) → its English lemmas, from the lines of one WordNet 3.0 data file."""
    synsets = {}
    for line in lines:
        if line.startswith("  ") or not line.strip():
            continue
        fields = line.split(" ")
        count = int(fields[3], 16)
        words = [fields[4 + 2 * i] for i in range(count)]
        # Adjectives carry their position as a suffix: (a), (p), (ip).
        words = [re.sub(r"\([a-z]+\)$", "", word).replace("_", " ") for word in words]
        synsets[f"{fields[0]}-{pos}"] = list(dict.fromkeys(words))
    return synsets


def read_wordnet(path):
    """Synset id → its English lemmas, from the WordNet 3.0 database archive."""
    synsets = {}
    with tarfile.open(path, "r:gz") as archive:
        for pos, name in DATA_FILES.items():
            member = archive.extractfile(f"dict/{name}")
            synsets.update(read_wordnet_lines(io.TextIOWrapper(member, encoding="latin-1"), pos))
    return synsets


def headword(text):
    """`(headword, note)` for a MonWN lemma, or None when it is left out."""
    text = " ".join(unicodedata.normalize("NFC", text).split())
    note = None
    match = NOTE.search(text)
    if match and match.start() > 0:
        note = " ".join((match.group(1) or match.group(2)).split())
        text = text[:match.start()]
    words = []
    for word in text.split(" "):
        if any(c.lower() in MONGOLIAN_LETTERS for c in word):
            word = word.translate(LOOK_ALIKES)
        words.append(word)
    text = " ".join(words)
    letters = [c for c in text if c.isalpha()]
    if not letters or not all(c.lower() in MONGOLIAN_LETTERS for c in letters):
        return None
    if any(not (c.isalnum() or c in " -") for c in text):
        return None
    return text, note


def definition(lemmas, note, mongolian):
    """A synset's definition: the note, the English lemmas, the Mongolian definition on a line of its own."""
    english = ", ".join(lemmas)
    if note:
        english = f"({note}) {english}"
    if not mongolian:
        return english
    return {
        "type": "structured-content",
        "content": [english, {"tag": "div", "content": mongolian}],
    }


def collect(lemmas, definitions, synsets):
    """`(headword, tag)` → its definitions, in the order of the MonWN file."""
    entries = {}
    seen = {}
    for synset, text in lemmas:
        english = synsets.get(synset)
        cleaned = headword(text)
        if not english or cleaned is None:
            continue
        word, note = cleaned
        key = (word, PARTS_OF_SPEECH[synset[-1]])
        shown = (note, tuple(english))
        if shown in seen.setdefault(key, set()):
            continue
        seen[key].add(shown)
        entries.setdefault(key, []).append(definition(english, note, definitions.get(synset)))
    return entries


def build(entries):
    """The files of the Yomitan archive, by name."""
    index = {
        "title": "MonWN mn-en",
        "format": 3,
        "revision": MONWN_COMMIT[:12],
        "sequenced": True,
        "author": "A. Chagnaa, A. Ganbold, K. Batsuren, O. Nyamdavaa, Z. Munkhjargal, E. Batbaatar",
        "url": MONWN_URL,
        "description": "Mongolian words of the Mongolian Wordnet with the English words of the Princeton WordNet "
                       "synsets they belong to, and the Mongolian definition where the Mongolian Wordnet has one. "
                       "Converted for Yomitan by Screenlate.",
        "attribution": "Mongolian Wordnet (MonWN), " + MONWN_URL + " (commit " + MONWN_COMMIT[:12] + "), "
                       "CC BY-SA 4.0 (the repository's license; its data file says CC BY 4.0). Batsuren, Ganbold, "
                       "Chagnaa, Giunchiglia: Building The Mongolian WordNet, GWC 2019. English lemmas from WordNet "
                       "3.0. WordNet 3.0 Copyright 2006 by Princeton University. All rights reserved. Permission "
                       "to use, copy, modify and distribute this software and database and its documentation for "
                       "any purpose and without fee or royalty is hereby granted, provided that you agree to comply "
                       "with the following copyright notice and statements, including the disclaimer, and that the "
                       "same appear on ALL copies of the software, database and documentation, including "
                       "modifications that you make for internal use or for distribution. THIS SOFTWARE AND "
                       "DATABASE IS PROVIDED \"AS IS\" AND PRINCETON UNIVERSITY MAKES NO REPRESENTATIONS OR "
                       "WARRANTIES, EXPRESS OR IMPLIED. BY WAY OF EXAMPLE, BUT NOT LIMITATION, PRINCETON UNIVERSITY "
                       "MAKES NO REPRESENTATIONS OR WARRANTIES OF MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR "
                       "PURPOSE OR THAT THE USE OF THE LICENSED SOFTWARE, DATABASE OR DOCUMENTATION WILL NOT "
                       "INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS. The name of "
                       "Princeton University or Princeton may not be used in advertising or publicity pertaining to "
                       "distribution of the software and/or database. Title to copyright in this software, database "
                       "and any associated documentation shall at all times remain with Princeton University and "
                       "LICENSEE agrees to preserve same.",
        "sourceLanguage": "mn",
        "targetLanguage": "en",
    }
    words = sorted({word for word, _ in entries})
    sequences = {word: number for number, word in enumerate(words, start=1)}
    terms = [
        [word, "", tag, "", 0, glosses, sequences[word], ""]
        for (word, tag), glosses in sorted(entries.items(), key=lambda item: (item[0][0], item[0][1]))
    ]
    files = {"index.json": index, "tag_bank_1.json": TAGS}
    for number, start in enumerate(range(0, len(terms), TERMS_PER_BANK), start=1):
        files[f"term_bank_{number}.json"] = terms[start:start + TERMS_PER_BANK]
    return files


def write_zip(files, out):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, value in files.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, json.dumps(value, ensure_ascii=False, separators=(",", ":")))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("monwn", help=f"wn-data-mon.tsv of {MONWN_URL} at commit {MONWN_COMMIT[:12]}")
    parser.add_argument("wordnet", help=f"the WordNet 3.0 database, {WORDNET_URL}")
    parser.add_argument("out", help="Yomitan archive to write")
    args = parser.parse_args(argv)
    for path, expected in ((args.monwn, MONWN_SHA256), (args.wordnet, WORDNET_SHA256)):
        if sha256(path) != expected:
            parser.error(f"{Path(path).name}: not the pinned file (SHA-256 {expected})")
    with open(args.monwn, encoding="utf-8") as stream:
        lemmas, definitions = read_monwn(stream)
    files = build(collect(lemmas, definitions, read_wordnet(args.wordnet)))
    write_zip(files, args.out)
    rows = [row for name, bank in files.items() if name.startswith("term_bank_") for row in bank]
    print(f"{files['index.json']['title']} {MONWN_COMMIT[:12]}: {len({row[0] for row in rows})} headwords, "
          f"{len(rows)} rows, {Path(args.out).stat().st_size >> 10} KB")


if __name__ == "__main__":
    sys.exit(main())
