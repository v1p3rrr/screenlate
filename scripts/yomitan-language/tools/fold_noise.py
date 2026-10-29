"""Extra results the folded marks bring a language (row "Marks folded in the lookup"), measured as the data check of
`ai/plans/2026-10-29-folded-marks-and-flags.md`.

    python -I fold_noise.py <list.tsv> <none|tt|ba> <dictionary.zip>...

<list.tsv> is a frequency list from freq_list.py; its 30,000 most frequent words are weighted by their counts, so the
shares are of running text. The dictionaries are Yomitan archives (wty's), every headword and form row counted.
Printed: noise, the share of running text whose word is found as written and also gets other spellings through the
fold, typed (the import's folded keys only) and after OCR (the text folded too); extras per such word; gain, the share
found only through the fold; the keys the import adds and their share of the rows; the commonest extras. The second
argument subtracts what the Russian-twin keys of Tatar or Bashkir give already (group 6 question 15); `none` for the
others. The fold is `foldMarks` of own-processors.js, its tables read from that file.
"""
import collections
import json
import os
import re
import sys
import unicodedata
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))

# A language's own letters as the Russian letters a keyboard or OCR gives for them (as KazakhSupport's twins).
TWINS = {
    "tt": {"ә": "а", "ө": "о", "ү": "у", "җ": "ж", "ң": "н", "һ": "х"},
    "ba": {"ә": "а", "ө": "о", "ү": "у", "ҡ": "к", "ғ": "г", "ҙ": "з", "ҫ": "с", "ң": "н", "һ": "х"},
}


def fold_tables():
    """FOLDED_SCRIPTS and FOLDED_LETTERS of own-processors.js."""
    source = open(os.path.join(HERE, "..", "own-processors.js"), encoding="utf-8").read()
    scripts = re.search(r"const FOLDED_SCRIPTS = \[(.*?)\];", source, re.S).group(1)
    letters = re.search(r"const FOLDED_LETTERS = .*?new Map\(\[(.*?)\]\);", source, re.S).group(1)
    pair = re.compile(r"\[(0x[0-9A-Fa-f]+), (0x[0-9A-Fa-f]+)\]")
    return ([(int(a, 16), int(b, 16)) for a, b in pair.findall(scripts)],
            {int(a, 16): int(b, 16) for a, b in pair.findall(letters)})


SCRIPTS, LETTERS = fold_tables()


def fold(text):
    """[text] without the marks after its Latin and Cyrillic letters, as `foldMarks` writes it."""
    out = []
    after_letter = False
    for ch in unicodedata.normalize("NFD", text):
        if unicodedata.category(ch) == "Mn":
            if after_letter:
                continue
        else:
            point = ord(ch)
            after_letter = unicodedata.category(ch).startswith("L") and any(a <= point <= b for a, b in SCRIPTS)
        out.append(chr(LETTERS.get(ord(ch), ord(ch))))
    return unicodedata.normalize("NFC", "".join(out))


def headwords(paths):
    """The headword of every term bank row of the archives at [paths]."""
    for path in paths:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if re.fullmatch(r"term_bank_\d+\.json", name):
                    yield from (row[0] for row in json.loads(archive.read(name)))


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    list_path, twin_language, *paths = sys.argv[1:]
    twins = TWINS.get(twin_language, {})

    def twin(text):
        return "".join(twins.get(c, c) for c in text)

    raw = list(headwords(paths))
    extra = sum(1 for k in raw if fold(k) != k)
    extra_net = sum(1 for k in raw if fold(k) != k and fold(k) != twin(k))
    keys = {k.lower() for k in raw}
    by_fold = collections.defaultdict(set)
    by_twin = collections.defaultdict(set)
    for k in keys:
        by_fold[fold(k)].add(k)
        if twins:
            by_twin[twin(k)].add(k)
    words = []
    for line in open(list_path, encoding="utf-8"):
        word, count = line.rstrip("\n").split("\t")
        words.append((word.lower(), float(count)))
        if len(words) == 30000:
            break
    total = sum(f for _, f in words)
    results = {}
    for mode in ("typed", "ocr"):
        noise = gain = weighted_extras = 0.0
        contribution = collections.Counter()
        extras_of = {}
        for word, f in words:
            found = by_fold.get(word if mode == "typed" else fold(word), set()) - {word}
            if mode == "typed":
                # Typed text already finds the keys whose Russian-letter spelling it is.
                found -= by_twin.get(word, set())
            if not found:
                continue
            if word in keys:
                noise += f
                weighted_extras += f * len(found)
                contribution[word] += f
                extras_of[word] = sorted(found)
            else:
                gain += f
        results[mode] = (100 * noise / total, weighted_extras / noise if noise else 0, 100 * gain / total,
                         [(w, extras_of[w]) for w, _ in contribution.most_common(4)])
    typed, ocr = results["typed"], results["ocr"]
    print(f"noise typed / OCR {typed[0]:.1f} / {ocr[0]:.1f}%; extras per word {typed[1]:.2f} / {ocr[1]:.2f}; "
          f"gain {typed[2]:.2f} / {ocr[2]:.2f}%; extra keys {extra} ({100 * extra / len(raw):.1f}% of {len(raw)} rows)"
          + (f", net of twins {extra_net} ({100 * extra_net / len(raw):.1f}%)" if twins else ""))
    print("  commonest typed:", typed[3])
    print("  commonest OCR:", ocr[3])


if __name__ == "__main__":
    main()
