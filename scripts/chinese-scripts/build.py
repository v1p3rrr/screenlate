"""Builds the table of Simplified and Traditional Chinese characters (ChineseScriptTable.kt).

    python scripts/chinese-scripts/build.py <Unihan.zip> <CC-CEDICT.zip> <output .kt>

Unihan.zip is the Unicode Han database (https://www.unicode.org/Public/<version>/ucd/Unihan.zip, Unicode License V3);
CC-CEDICT.zip is the Yomitan build of CC-CEDICT from MarvNC/cc-cedict-yomitan (CC BY-SA 4.0), whose entries name the
Traditional and the Simplified spelling of each word.

A character is Simplified only when no Traditional spelling in CC-CEDICT uses it and it either replaces a Traditional
character there or Unihan lists it in the mainland sets (GB 2312, the 2013 general standard) but not in Big5, with a
Traditional variant. Traditional only is the same the other way round. Characters both scripts use (后, 着 in Taiwan,
里) are in neither set. The forms of a character in the other script are ordered by how many CC-CEDICT words write it
so, then Unihan's variants follow.
"""
import collections
import json
import re
import sys
import zipfile

HEADWORD = re.compile(r'"content": "([^"]*)", "lang": "zh-(Hans|Hant)", "data": \{"cccedict": "headword-(?:simp|trad)"\}')


def unihan(path):
    fields = collections.defaultdict(dict)
    with zipfile.ZipFile(path) as archive:
        for name in ("Unihan_Variants.txt", "Unihan_OtherMappings.txt"):
            for line in archive.read(name).decode("utf-8").splitlines():
                if line.startswith("#") or not line.strip():
                    continue
                code, field, value = line.split("\t")
                fields[field][chr(int(code[2:], 16))] = value
    return fields


def variants(fields, field, char):
    value = fields[field].get(char)
    return [chr(int(item[2:].split("<")[0], 16)) for item in value.split()] if value else []


def cedict_pairs(path):
    """Counts of (Simplified, Traditional) character pairs at the same place of a word, and where each one occurs."""
    pairs = collections.Counter()
    in_traditional = collections.Counter()
    in_simplified = collections.Counter()
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.startswith("term_bank"):
                continue
            for row in json.loads(archive.read(name)):
                spellings = dict((tag, text) for text, tag in HEADWORD.findall(json.dumps(row[5], ensure_ascii=False)))
                simplified = spellings.get("Hans")
                traditional = spellings.get("Hant", simplified)
                if simplified is None or len(simplified) != len(traditional) or row[0] not in (simplified, traditional):
                    continue
                # Each word comes once per spelling; count it once.
                if row[0] != simplified:
                    continue
                for s, t in zip(simplified, traditional):
                    pairs[(s, t)] += 1
                    in_simplified[s] += 1
                    in_traditional[t] += 1
    return pairs, in_simplified, in_traditional


def is_han(char):
    code = ord(char)
    return 0x3400 <= code <= 0x9FFF or 0xF900 <= code <= 0xFAFF or 0x20000 <= code <= 0x323AF


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    fields = unihan(sys.argv[1])
    pairs, in_simplified, in_traditional = cedict_pairs(sys.argv[2])
    big5 = set(fields["kBigFive"])
    mainland = set(fields["kGB0"]) | set(fields["kTGH"])
    replaced = {s for (s, t) in pairs if s != t}
    replacing = {t for (s, t) in pairs if s != t}

    simplified_only = {
        c for c in replaced | (mainland - big5)
        if in_traditional[c] == 0 and (c in replaced or any(v != c for v in variants(fields, "kTraditionalVariant", c)))
    }
    traditional_only = {
        c for c in replacing | (big5 - mainland)
        if in_simplified[c] == 0 and (c in replacing or any(v != c for v in variants(fields, "kSimplifiedVariant", c)))
    }
    simplified_only = {c for c in simplified_only if is_han(c)}
    traditional_only = {c for c in traditional_only if is_han(c)}

    def forms(source_index, unihan_field, sources, source_only):
        result = {}
        for char in sorted(sources):
            counted = collections.Counter()
            for pair, count in pairs.items():
                if pair[source_index] == char:
                    counted[pair[1 - source_index]] += count
            ordered = [c for c, _ in sorted(counted.items(), key=lambda item: (-item[1], item[0]))]
            ordered += [v for v in variants(fields, unihan_field, char) if v not in ordered and is_han(v)]
            # Unihan lists some characters as their own variant (头); one only its script uses is never the other's.
            if char in source_only:
                ordered = [c for c in ordered if c != char]
            if any(c != char for c in ordered):
                result[char] = ordered
        return result

    traditional_forms = forms(0, "kTraditionalVariant", replaced | simplified_only, simplified_only)
    simplified_forms = forms(1, "kSimplifiedVariant", replacing | traditional_only, traditional_only)

    def groups(table):
        return " ".join(char + "".join(candidates) for char, candidates in table.items())

    revision = re.search(r"\d{4}-\d{2}-\d{2}", zipfile.ZipFile(sys.argv[2]).read("index.json").decode("utf-8"))
    unicode_version = re.search(r"# Unicode Version ([\d.]+)",
                                zipfile.ZipFile(sys.argv[1]).read("Unihan_Variants.txt").decode("utf-8"))
    source = (
        "package com.vpr.screenlate.core.common.language\n\n"
        "/*\n"
        f" * Generated by scripts/chinese-scripts/build.py from Unihan {unicode_version[1]} (Unicode License V3) and\n"
        f" * CC-CEDICT {revision[0]} (CC BY-SA 4.0); see NOTICE. Do not edit.\n"
        " */\n"
        "internal object ChineseScriptTable {\n"
        "    /** Characters only Simplified text uses. */\n"
        f"    const val SIMPLIFIED_ONLY = \"{''.join(sorted(simplified_only))}\"\n\n"
        "    /** Characters only Traditional text uses. */\n"
        f"    const val TRADITIONAL_ONLY = \"{''.join(sorted(traditional_only))}\"\n\n"
        "    /** A character, then its Traditional forms, the most common first; groups are separated by spaces. */\n"
        f"    const val TRADITIONAL_FORMS = \"{groups(traditional_forms)}\"\n\n"
        "    /** A character, then its Simplified forms, the most common first; groups are separated by spaces. */\n"
        f"    const val SIMPLIFIED_FORMS = \"{groups(simplified_forms)}\"\n"
        "}\n"
    )
    for name in ("SIMPLIFIED_ONLY", "TRADITIONAL_ONLY", "TRADITIONAL_FORMS", "SIMPLIFIED_FORMS"):
        value = re.search(name + r' = "([^"]*)"', source)[1]
        # A string constant in a class file holds at most 65535 bytes of modified UTF-8.
        assert len(value.encode("utf-8")) < 60000, name
    with open(sys.argv[3], "w", encoding="utf-8", newline="\n") as out:
        out.write(source)
    print(f"{len(simplified_only)} Simplified only, {len(traditional_only)} Traditional only, "
          f"{len(traditional_forms)} with Traditional forms, {len(simplified_forms)} with Simplified forms")


if __name__ == "__main__":
    main()
