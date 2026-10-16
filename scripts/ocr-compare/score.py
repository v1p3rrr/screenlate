#!/usr/bin/env python3
"""Scores the engines of the on-device OCR comparison against the ground truth that collect.mjs wrote.

    python scripts/ocr-compare/score.py <ground truth folder> <results folder> [--details]

The results folder holds `<engine>.json` as OcrCompareTest writes them. Per engine and language:

- character error rate: for each ground-truth region, the words the engine read whose centers lie inside it, in
  reading order, against the region's text (edit distance over the region's characters);
- words read whole: the region's words found among those words, punctuation around them ignored;
- taps (web pages only): a tap at the center of each word lands in a word box the engine read as that word;
- time: the median of the timed runs per screen, averaged over the screens.
"""

import argparse
import json
import statistics
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

#: Pixels a word's center may lie outside a region and still count as in it.
MARGIN = 8

PUNCTUATION = " \t.,;:!?«»\"'“”„()[]{}—–-…·•"


def normalize(text):
    return " ".join(unicodedata.normalize("NFC", text).split())


def bare(word):
    return unicodedata.normalize("NFC", word).strip(PUNCTUATION)


def distance(a, b):
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def inside(box, x, y, margin=0):
    return box[0] - margin <= x <= box[2] + margin and box[1] - margin <= y <= box[3] + margin


def center(box):
    return (box[0] + box[2]) / 2, (box[1] + box[3]) / 2


def score_image(truth, result):
    """Edits, characters, words found, words, taps right, taps for one screen."""
    words = []
    for order, line in enumerate(sorted(result["lines"], key=lambda l: (l["box"][1], l["box"][0]))):
        for position, word in enumerate(line["words"]):
            words.append((order, position, word))
    totals = Counter()
    for region in truth["regions"]:
        found = sorted(
            (w for w in words if inside(region["box"], *center(w[2]["box"]), MARGIN)),
            key=lambda w: (w[0], w[1]),
        )
        read = normalize(" ".join(w[2]["text"] for w in found))
        expected = normalize(region["text"])
        totals["edits"] += distance(read, expected)
        totals["chars"] += len(expected)
        expected_words = Counter(bare(w) for w in expected.split() if bare(w))
        read_words = Counter(bare(w[2]["text"]) for w in found if bare(w[2]["text"]))
        totals["words_found"] += sum((expected_words & read_words).values())
        totals["words"] += sum(expected_words.values())
        for word in region.get("words", []):
            target = bare(word["text"])
            if not target:
                continue
            x, y = center(word["box"])
            hit = next((w[2] for w in words if inside(w[2]["box"], x, y)), None)
            totals["taps"] += 1
            totals["taps_right"] += hit is not None and bare(hit["text"]) == target
    return totals


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("truth", type=Path)
    parser.add_argument("results", type=Path)
    parser.add_argument("--details", action="store_true", help="a line per screen too")
    args = parser.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    truths = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in args.truth.glob("*.json")}
    rows = []
    for path in sorted(args.results.glob("*.json")):
        engine = path.stem
        by_language = defaultdict(lambda: {"totals": Counter(), "times": [], "screens": 0})
        for result in json.loads(path.read_text(encoding="utf-8")):
            truth = truths.get(Path(result["image"]).stem)
            if truth is None:
                continue
            totals = score_image(truth, result)
            entry = by_language[result["language"]]
            entry["totals"] += totals
            entry["times"].append(statistics.median(result["times_ms"]))
            entry["screens"] += 1
            if args.details:
                print(f"  {engine:<22} {result['image']:<32} CER {totals['edits'] / max(1, totals['chars']):6.1%}  "
                      f"words {totals['words_found'] / max(1, totals['words']):6.1%}  "
                      f"{statistics.median(result['times_ms']):6.0f} ms")
        for language, entry in sorted(by_language.items()):
            t = entry["totals"]
            rows.append((
                engine, language, entry["screens"],
                t["edits"] / max(1, t["chars"]),
                t["words_found"] / max(1, t["words"]),
                t["taps_right"] / t["taps"] if t["taps"] else None,
                statistics.mean(entry["times"]),
            ))
    print(f"{'engine':<22} {'lang':<5} {'screens':>7} {'CER':>7} {'words':>7} {'taps':>7} {'ms/screen':>10}")
    for engine, language, screens, cer, words, taps, ms in sorted(rows, key=lambda r: (r[1], r[0])):
        taps_text = f"{taps:7.1%}" if taps is not None else f"{'-':>7}"
        print(f"{engine:<22} {language:<5} {screens:>7} {cer:7.1%} {words:7.1%} {taps_text} {ms:10.0f}")


if __name__ == "__main__":
    sys.exit(main())
