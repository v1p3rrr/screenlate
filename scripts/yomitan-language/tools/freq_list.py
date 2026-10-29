"""Writes a frequency list as `word<TAB>count` lines, the most frequent first, for coverage.mjs and fold_noise.py.

    python -I freq_list.py <large_xx.msgpack.gz | corpus.tar.gz> [--lowercase-forms] > list.tsv

A wordfreq list (`wordfreq/data/*.msgpack.gz` at the commit of `scripts/frequency/wordfreq_catalog.py`) gives each word
the weight of its bucket, 10^(-bucket/100); a Leipzig archive's `*-words.txt` gives counts. Leipzig words are lowercased
and merged, or with --lowercase-forms only the forms the corpus writes in lowercase are kept (the lists of the group 6
measurements, `ai/plans/2026-10-20-group-6-research.md`). Only words with a letter are written.
"""
import collections
import gzip
import io
import struct
import sys
import tarfile


def unpack(b, i):
    """The msgpack value at [i] of [b] and the position after it (the types wordfreq's files use)."""
    t = b[i]
    if t <= 0x7F:
        return t, i + 1
    if t >= 0xE0:
        return t - 0x100, i + 1
    if 0x80 <= t <= 0x8F:
        return unpack_map(b, i + 1, t & 0x0F)
    if 0x90 <= t <= 0x9F:
        return unpack_array(b, i + 1, t & 0x0F)
    if 0xA0 <= t <= 0xBF:
        n = t & 0x1F
        return b[i + 1:i + 1 + n].decode("utf-8"), i + 1 + n
    if t == 0xC0:
        return None, i + 1
    if t in (0xC2, 0xC3):
        return t == 0xC3, i + 1
    if t in (0xD9, 0xDA, 0xDB):
        size = {0xD9: 1, 0xDA: 2, 0xDB: 4}[t]
        n = int.from_bytes(b[i + 1:i + 1 + size], "big")
        return b[i + 1 + size:i + 1 + size + n].decode("utf-8"), i + 1 + size + n
    if t in (0xDC, 0xDD):
        size = 2 if t == 0xDC else 4
        return unpack_array(b, i + 1 + size, int.from_bytes(b[i + 1:i + 1 + size], "big"))
    if t in (0xDE, 0xDF):
        size = 2 if t == 0xDE else 4
        return unpack_map(b, i + 1 + size, int.from_bytes(b[i + 1:i + 1 + size], "big"))
    if t in (0xCC, 0xCD, 0xCE, 0xCF):
        size = {0xCC: 1, 0xCD: 2, 0xCE: 4, 0xCF: 8}[t]
        return int.from_bytes(b[i + 1:i + 1 + size], "big"), i + 1 + size
    if t == 0xCB:
        return struct.unpack(">d", b[i + 1:i + 9])[0], i + 9
    raise ValueError(f"msgpack type {t:#x} at {i}")


def unpack_array(b, i, n):
    out = []
    for _ in range(n):
        value, i = unpack(b, i)
        out.append(value)
    return out, i


def unpack_map(b, i, n):
    out = {}
    for _ in range(n):
        key, i = unpack(b, i)
        out[key], i = unpack(b, i)
    return out, i


def wordfreq(path):
    """(word, weight) of a wordfreq list: a header, then one list of words per bucket of a hundredth of a decade."""
    data, _ = unpack(gzip.open(path).read(), 0)
    return [(word, 10 ** (-bucket / 100)) for bucket, words in enumerate(data[1:]) for word in words]


def leipzig(path, lowercase_forms):
    """(word, count) of a Leipzig archive's words file, `id<TAB>word<TAB>count` lines."""
    counts = collections.Counter()
    with tarfile.open(path) as archive:
        for member in archive.getmembers():
            if not member.name.endswith("-words.txt"):
                continue
            for line in io.TextIOWrapper(archive.extractfile(member), encoding="utf-8", errors="replace"):
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3 or not parts[2].isdigit():
                    continue
                word = parts[1]
                if lowercase_forms and word != word.lower():
                    continue
                counts[word.lower()] += int(parts[2])
    return counts.most_common()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 1:
        raise SystemExit(__doc__)
    path = args[0]
    words = wordfreq(path) if path.endswith(".msgpack.gz") else leipzig(path, "--lowercase-forms" in sys.argv)
    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    for word, count in words:
        if any(c.isalpha() for c in word):
            out.write(f"{word}\t{count}\n")
    out.flush()


if __name__ == "__main__":
    main()
