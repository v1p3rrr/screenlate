#!/usr/bin/env python3
"""Scores the engines of the on-device OCR comparison against the ground truth that collect.mjs wrote.

    python scripts/ocr-compare/score.py <ground truth folder> <results folder> [--details | --show <screen>]
        [--by language|style|language-style]

The results folder holds `<engine>.json` as OcrCompareTest writes them. Per engine and language (or the style of the
regions, as hard.html marks them, or both):

- character error rate: for each ground-truth region, the words the engine read whose centers lie inside it, in
  reading order, against the region's text (edit distance over the region's characters). Reading order puts the
  engine's lines into rows (a line whose center lies within half a line's height of the row's first line joins
  it), each row's lines left to right (right to left for Arabic, Persian and Hebrew), each line's words in the engine's order; so a
  line the engine split into several boxes reads as one;
- words read whole: the region's words found among those words, punctuation around them ignored;
- taps (web pages only): a tap at the center of each word lands in a word box the engine read as that word;
- time: the median of the timed runs per screen, averaged over the screens.

A region turned by an angle (a tilted line of hard.html) holds the words whose centers lie inside its turned
rectangle, read along its direction; a character cut from a word lies on the word's slope, not at its middle height.

Words an engine glued together (an empty separator after the first: cloud recognition gives punctuation and Korean
particles as words of their own) count as one word, as in the app's line text.

Text is compared after NFKC, so full-width and half-width forms (，and ,) count as the same character, and so do the
LOOKALIKES below.

Japanese and Chinese are scored by characters: each word the engine read is split into its characters (by the
engine's character boxes, or evenly along the word), the error rate ignores spaces, a tap is on a character, and
words read whole are not counted. The error rate of Thai, Lao and Khmer ignores spaces too, and their words and taps
are not counted (their engines split words at spaces only, between phrases). Vertical Japanese (javert) reads each region from top to
bottom.
"""

import argparse
import json
import math
import statistics
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

#: Pixels a word's center may lie outside a region and still count as in it.
MARGIN = 8

#: Languages scored by characters; their image names start with these.
BY_CHARACTER = {"ja", "javert", "zh", "zht"}

#: Languages whose error rate ignores spaces.
UNSPACED = BY_CHARACTER | {"th", "lo", "km"}

#: Languages scored by their error rate alone.
NO_WORDS = {"th", "lo", "km"}

#: Languages whose rows read right to left.
RIGHT_TO_LEFT = {"ar", "fa", "he"}

PUNCTUATION = " \t.,;:!?«»\"'“”„()[]{}—–-…·•、。，．・「」『』（）！？：；։՝՞।॥،؟؛|"

#: Punctuation that looks the same as another sign and is read as it: the Armenian full stop as a colon, the
#: Devanagari danda as a vertical bar.
LOOKALIKES = str.maketrans({"։": ":", "|": "।"})

#: Letters of one language that engines read as their look-alikes from another: Persian ye and kaf as the Arabic ones
#: (the same shapes inside a word; a Persian lookup folds them anyway).
LANGUAGE_LOOKALIKES = {"fa": str.maketrans({"ي": "ی", "ى": "ی", "ك": "ک"})}


def normalize(text, spaced=True, language=""):
    text = unicodedata.normalize("NFKC", text).translate(LOOKALIKES).translate(LANGUAGE_LOOKALIKES.get(language, {}))
    return (" " if spaced else "").join(text.split())


def bare(word, language=""):
    return normalize(word, language=language).strip(PUNCTUATION)


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


def in_region(region, x, y):
    """Whether (x, y) lies in [region], or within MARGIN of it; in its turned rectangle if it has an angle."""
    if "angle" not in region:
        return inside(region["box"], x, y, MARGIN)
    quad = region["quad"]
    cx, cy = sum(p[0] for p in quad) / 4, sum(p[1] for p in quad) / 4
    a = math.radians(region["angle"])
    along = (x - cx) * math.cos(a) + (y - cy) * math.sin(a)
    across = -(x - cx) * math.sin(a) + (y - cy) * math.cos(a)
    return (abs(along) <= math.dist(quad[0], quad[1]) / 2 + MARGIN and
            abs(across) <= math.dist(quad[1], quad[2]) / 2 + MARGIN)


def sloped_center(word, angle):
    """[word]'s center; for a character cut from a word of a line turned by [angle], on the word's slope."""
    x, y = center(word["box"])
    if not angle or "word_box" not in word:
        return x, y
    wx, wy = center(word["word_box"])
    return x, wy + (x - wx) * math.tan(math.radians(angle))


def reading_order(found, right_to_left=False):
    """[found] ((line box, position, word) entries) by rows of lines from the top, each row along its direction."""
    rows = []
    for box in sorted({tuple(e[0]) for e in found}, key=lambda b: center(b)[1]):
        y, height = center(box)[1], box[3] - box[1]
        if rows and abs(y - rows[-1][0]) <= max(height, rows[-1][1]) / 2:
            rows[-1][2].append(box)
        else:
            rows.append((y, height, [box]))
    sign = -1 if right_to_left else 1
    rank = {box: (r, sign * center(box)[0]) for r, (_, _, boxes) in enumerate(rows) for box in boxes}
    return sorted(found, key=lambda e: (rank[tuple(e[0])], e[1]))


def characters(word, vertical):
    """The characters of an engine's word with their boxes: the engine's own, or the word's box cut evenly.

    The engine's boxes are used only when there is one per character and each has an area and lies inside the word's
    box (Tesseract's vertical models report symbol boxes in rotated coordinates). They are widened to the word's
    height (width for vertical text) and along the line to halfway to their neighbours, as the app does for taps.
    """
    text = [c for c in unicodedata.normalize("NFKC", word["text"]) if not c.isspace()]
    boxes = word.get("chars")
    l, t, r, b = word["box"]
    usable = boxes and len(boxes) == len(text) and all(
        x0 < x1 and y0 < y1 and inside(word["box"], *center([x0, y0, x1, y1]), MARGIN) for x0, y0, x1, y1 in boxes)
    if not usable:
        n = max(1, len(text))
        if vertical:
            boxes = [[l, t + (b - t) * i / n, r, t + (b - t) * (i + 1) / n] for i in range(n)]
        else:
            boxes = [[l + (r - l) * i / n, t, l + (r - l) * (i + 1) / n, b] for i in range(n)]
    else:
        a, z = (1, 3) if vertical else (0, 2)
        edges = [word["box"][a]] + [(boxes[i][z] + boxes[i + 1][a]) / 2 for i in range(len(boxes) - 1)] + [word["box"][z]]
        if vertical:
            boxes = [[l, edges[i], r, edges[i + 1]] for i in range(len(boxes))]
        else:
            boxes = [[edges[i], t, edges[i + 1], b] for i in range(len(boxes))]
    return [{"text": c, "box": box, "word_box": word["box"]} for c, box in zip(text, boxes)]


def glued(words):
    """[words] with each run the engine joined by empty separators made into one word."""
    result = []
    for word in words:
        if result and result[-1].get("separator") == "":
            previous = result[-1]
            box = [min(previous["box"][0], word["box"][0]), min(previous["box"][1], word["box"][1]),
                   max(previous["box"][2], word["box"][2]), max(previous["box"][3], word["box"][3])]
            result[-1] = {"text": previous["text"] + word["text"], "box": box, "separator": word.get("separator")}
        else:
            result.append(word)
    return result


def engine_words(result):
    """The engine's words as (line box, position, word), characters instead of words where scored by character."""
    by_character = result.get("language", "") in BY_CHARACTER
    words = []
    for line in result["lines"]:
        for position, word in enumerate(line["words"] if by_character else glued(line["words"])):
            if by_character:
                vertical = line.get("vertical", False)
                for offset, character in enumerate(characters(word, vertical)):
                    words.append((line["box"], (position, offset), character))
            else:
                words.append((line["box"], position, word))
    return words


def region_reads(truth, result, words):
    """Per ground-truth region: the region, the engine's words in it in reading order, what they read, the truth."""
    language = result.get("language", "")
    spaced = language not in UNSPACED
    for region in truth["regions"]:
        found = [w for w in words if in_region(region, *sloped_center(w[2], region.get("angle")))]
        if language == "javert":
            found.sort(key=lambda w: center(w[2]["box"])[1])
        elif "angle" in region:
            a, sign = math.radians(region["angle"]), -1 if language in RIGHT_TO_LEFT else 1

            def along(w):
                x, y = center(w[2]["box"])
                return sign * (x * math.cos(a) + y * math.sin(a))

            found.sort(key=along)
        else:
            found = reading_order(found, language in RIGHT_TO_LEFT)
        read = normalize(" ".join(w[2]["text"] for w in found), spaced, language)
        yield region, found, read, normalize(region["text"], spaced, language)


def score_image(truth, result, style=None):
    """Edits, characters, words found, words, taps right, taps for one screen; only its [style] regions if given."""
    language = result.get("language", "")
    by_character = language in BY_CHARACTER
    words = engine_words(result)
    totals = Counter()
    for region, found, read, expected in region_reads(truth, result, words):
        if style is not None and region.get("style") != style:
            continue
        totals["edits"] += distance(read, expected)
        totals["chars"] += len(expected)
        if not by_character and language not in NO_WORDS:
            expected_words = Counter(bare(w, language) for w in expected.split() if bare(w, language))
            read_words = Counter(bare(w[2]["text"], language) for w in found if bare(w[2]["text"], language))
            totals["words_found"] += sum((expected_words & read_words).values())
            totals["words"] += sum(expected_words.values())
        for word in [] if language in NO_WORDS else region.get("words", []):
            target = bare(word["text"], language)
            if not target:
                continue
            x, y = center(word["box"])
            hit = next((w[2] for w in words if inside(w[2]["box"], x, y)), None)
            totals["taps"] += 1
            totals["taps_right"] += hit is not None and bare(hit["text"], language) == target
    return totals


def show(truth, screen, results):
    for path in sorted(results.glob("*.json")):
        result = next((r for r in json.loads(path.read_text(encoding="utf-8")) if Path(r["image"]).stem == screen), None)
        if result is None:
            continue
        reads = list(region_reads(truth, result, engine_words(result)))
        wrong = sorted(((distance(read, expected), read, expected) for _, _, read, expected in reads), reverse=True)
        print(f"== {path.stem}: {sum(1 for w in wrong if w[0])} of {len(reads)} regions misread")
        for edits, read, expected in [w for w in wrong if w[0]][:5]:
            print(f"   {edits:3}  truth: {expected}")
            print(f"        read:  {read}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("truth", type=Path)
    parser.add_argument("results", type=Path)
    parser.add_argument("--details", action="store_true", help="a line per screen too")
    parser.add_argument("--show", metavar="SCREEN", help="each engine's misread regions on this screen (image name)")
    parser.add_argument("--by", choices=["language", "style", "language-style"], default="language",
                        help="what a row of the table stands for")
    args = parser.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    truths = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in args.truth.glob("*.json")}
    if args.show:
        show(truths[args.show], args.show, args.results)
        return 0
    rows = []
    for path in sorted(args.results.glob("*.json")):
        engine = path.stem
        by_language = defaultdict(lambda: {"totals": Counter(), "times": [], "screens": 0})
        for result in json.loads(path.read_text(encoding="utf-8")):
            truth = truths.get(Path(result["image"]).stem)
            if truth is None:
                continue
            for key, totals in group_scores(truth, result, args.by):
                entry = by_language[key]
                entry["totals"] += totals
                entry["times"].append(statistics.median(result["times_ms"]))
                entry["screens"] += 1
            totals = score_image(truth, result)
            if args.details:
                words = f"{totals['words_found'] / totals['words']:6.1%}" if totals["words"] else f"{'-':>6}"
                print(f"  {engine:<22} {result['image']:<32} CER {totals['edits'] / max(1, totals['chars']):6.1%}  "
                      f"words {words}  {statistics.median(result['times_ms']):6.0f} ms")
        for language, entry in sorted(by_language.items()):
            t = entry["totals"]
            rows.append((
                engine, language, entry["screens"],
                t["edits"] / max(1, t["chars"]),
                t["words_found"] / t["words"] if t["words"] else None,
                t["taps_right"] / t["taps"] if t["taps"] else None,
                statistics.mean(entry["times"]),
            ))
    print(f"{'engine':<22} {args.by:<6} {'screens':>7} {'CER':>7} {'words':>7} {'taps':>7} {'ms/screen':>10}")
    for engine, language, screens, cer, words, taps, ms in sorted(rows, key=lambda r: (r[1], r[0])):
        taps_text = f"{taps:7.1%}" if taps is not None else f"{'-':>7}"
        words_text = f"{words:7.1%}" if words is not None else f"{'-':>7}"
        print(f"{engine:<22} {language:<6} {screens:>7} {cer:7.1%} {words_text} {taps_text} {ms:10.0f}")


def group_scores(truth, result, by):
    """(row key, totals) of one screen: one row for its language, or one per style of its regions."""
    language = result["language"]
    if by == "language":
        return [(language, score_image(truth, result))]
    styles = sorted({region.get("style") or "-" for region in truth["regions"]})
    return [(style if by == "style" else f"{language}/{style}",
             score_image(truth, result, None if style == "-" else style)) for style in styles]


if __name__ == "__main__":
    sys.exit(main())
