#!/usr/bin/env python3
"""Converts a FreeDict dictionary (TEI P5, as WikDict generates them) into a Yomitan dictionary archive.

    python scripts/freedict/freedict_yomitan.py <freedict-deu-rus-2025.11.23.src.tar.xz | deu-rus.tei> <out.zip>

Each sense becomes one definition: its translations as a list, the source-language definition that tells the senses
apart above it. Parts of speech and the genders of nouns become tags; the part of speech also becomes the rule
Yomitan's deinflection matches. Chinese headwords get the pinyin of their first pronunciation as the reading.

Older FreeDict files (slv-eng) name no languages in the text: they come from the file name (`slv-eng.tei`), and the
authors from the header's `respStmt` instead of WikDict's maintainer. Usage examples are left out.
"""

import argparse
import html
import io
import json
import re
import sys
import tarfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

TEI = "{http://www.tei-c.org/ns/1.0}"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"

#: WikDict's part-of-speech names as short tags, the way Wiktionary dictionaries for Yomitan write them.
TAGS = {
    "pn": "name",
    "Adverb": "adv",
    "pronominalAdverb": "adv",
    "ordinalAdjective": "adj",
    "possessiveAdjective": "adj",
    "participleAdjective": "adj",
    "modal": "v",
    "numeral": "num",
    "cardinalNumeral": "num",
    "indefiniteCardinalNumeral": "num",
    "numeralFraction": "num",
    "collective": "num",
    "pronoun": "pron",
    "personalPronoun": "pron",
    "possessivePronoun": "pron",
    "demonstrativePronoun": "pron",
    "interrogativePronoun": "pron",
    "indefinitePronoun": "pron",
    "relativePronoun": "pron",
    "preposition": "prep",
    "postposition": "postp",
    "conjunction": "conj",
    "interjection": "intj",
    "determiner": "det",
    "article": "art",
    "phraseologicalUnit": "phrase",
    "expression": "phrase",
    "abbreviation": "abbr",
    "acronym": "abbr",
    "int": "intj",
    "vt": "v",
    "vi": "v",
}

#: Older FreeDict files' genders.
GENDER_TAGS = {"f": "fem", "m": "masc", "n": "neut"}

#: Parts of speech whose gender is the word's own, not that of the form written (an adjective's masculine).
GENDERED = {"n", "name", ""}

#: Languages of FreeDict file names (ISO 639-3) as the app's codes, for files whose text names none.
LANGUAGES = {
    "dan": "da", "deu": "de", "eng": "en", "epo": "eo", "fra": "fr", "ita": "it", "jpn": "ja", "kor": "ko",
    "nld": "nl", "nor": "no", "pol": "pl", "por": "pt", "rus": "ru", "slv": "sl", "spa": "es", "swe": "sv",
    "tur": "tr", "vie": "vi", "zho": "zh",
}

#: Parts of speech that Yomitan's deinflection rules know, under the condition names they use.
RULES = {"v": "v", "n": "n", "adj": "adj", "adv": "adv"}

#: Tag notes in the gloss language, by tag; other tags get their name as the note.
TAG_NOTES = {
    "ru": {
        "n": "существительное",
        "name": "имя собственное",
        "v": "глагол",
        "adj": "прилагательное",
        "adv": "наречие",
        "num": "числительное",
        "pron": "местоимение",
        "prep": "предлог",
        "postp": "послелог",
        "conj": "союз",
        "intj": "междометие",
        "particle": "частица",
        "det": "определитель",
        "art": "артикль",
        "prefix": "приставка",
        "suffix": "суффикс",
        "phrase": "устойчивое выражение",
        "proverb": "пословица",
        "idiom": "идиома",
        "abbr": "сокращение",
        "symbol": "символ",
        "letter": "буква",
        "masc": "мужской род",
        "fem": "женский род",
        "neut": "средний род",
    },
}

GENDERS = {"masc", "fem", "neut"}

#: Pinyin as WikDict writes it in the first <pron> of a Chinese entry: `-{-{xīnyōu}-}-`.
PINYIN = re.compile(r"^-\{-\{(.+)\}-\}-$")

TERMS_PER_BANK = 10_000

STYLES = """div[data-sc-content="sense-label"] { font-style: italic; opacity: 0.75; }
ul[data-sc-content="glossary"] { margin: 0; padding-left: 1.2em; }
"""


#: WikDict's links inside definitions, `#{target|shown text}#`; `[[` is left where it cut a Wiktionary link off.
LINK = re.compile(r"#[{](?:[^|}]*[|])?([^}]*)[}]#")
#: What is left of a template WikDict could not read (`}#|tellelig|kat`, `Verb|minner]] om`).
RESIDUE = re.compile(r"[}]#|#[{]|[|]|]]|[{][{]")


def text(element):
    return "".join(element.itertext()).strip() if element is not None else ""


def plain(definition):
    """A definition as one line without WikDict's link markup and HTML entities; empty when only a broken template is
    left."""
    line = " ".join(html.unescape(LINK.sub(lambda m: m.group(1), definition)).replace("[[", "").split())
    return "" if RESIDUE.search(line) else line


def unique(items):
    seen = set()
    return [item for item in items if item and not (item in seen or seen.add(item))]


def read_header(header):
    """Title, edition, authors, license sentence and license URL of a TEI header, and whether WikDict made it.

    The license sentence ends with the paragraph that links the license: older headers go on with its boilerplate.
    """
    title = text(header.find(f"{TEI}fileDesc/{TEI}titleStmt/{TEI}title"))
    edition = text(header.find(f"{TEI}fileDesc/{TEI}editionStmt/{TEI}edition"))
    authors = unique(text(name) for name in header.iterfind(f"{TEI}fileDesc/{TEI}titleStmt/{TEI}respStmt/{TEI}name"))
    availability = header.find(f"{TEI}fileDesc/{TEI}publicationStmt/{TEI}availability")
    paragraphs = list(availability.iterfind(f"{TEI}p")) if availability is not None else []
    linked = next((i for i, p in enumerate(paragraphs) if p.find(f".//{TEI}ref") is not None), None)
    if linked is not None:
        license_text = " ".join(" ".join(text(p) for p in paragraphs[:linked + 1]).split())
    else:
        license_text = " ".join(text(availability).split()) if availability is not None else ""
    ref = availability.find(f".//{TEI}ref") if availability is not None else None
    license_url = ref.get("target", "") if ref is not None else ""
    source_desc = header.find(f"{TEI}fileDesc/{TEI}sourceDesc")
    wikdict = source_desc is not None and any(
        "wikdict" in ref.get("target", "") for ref in source_desc.iterfind(f".//{TEI}ref")
    )
    return {
        "title": title, "edition": edition, "authors": authors, "license": license_text, "license_url": license_url,
        "wikdict": wikdict,
    }


def read_entry(entry, source):
    """The rows of one <entry>: `(headword, reading, tags, rule, senses)`, senses as `(label, translations)`."""
    headwords = unique(text(orth) for orth in entry.iterfind(f"{TEI}form/{TEI}orth"))
    reading = ""
    if source == "zh":
        first = entry.find(f"{TEI}form/{TEI}pron")
        match = PINYIN.match(text(first)) if first is not None else None
        reading = match.group(1).strip() if match else ""
    gram = entry.find(f"{TEI}gramGrp")
    pos = text(gram.find(f"{TEI}pos")) if gram is not None else ""
    pos = TAGS.get(pos, pos)
    genders = [GENDER_TAGS.get(text(g), text(g)) for g in gram.iterfind(f"{TEI}gen")] if gram is not None else []
    tags = unique([pos] + [g for g in genders if g in GENDERS and pos in GENDERED])
    senses = []
    for sense in entry.iterfind(f"{TEI}sense"):
        translations = unique(
            text(quote)
            for cit in sense.iterfind(f"{TEI}cit")
            if cit.get("type") == "trans"
            for quote in cit.iterfind(f"{TEI}quote")
        )
        if not translations:
            continue
        label = "; ".join(unique(plain(text(d)) for d in sense.iterfind(f".//{TEI}def")))
        senses.append((label, translations))
    if not senses:
        return []
    return [(headword, reading, tags, RULES.get(pos, ""), senses) for headword in headwords]


def glossary(senses):
    """One structured-content definition per sense, laid out as Wiktionary dictionaries for Yomitan do."""
    result = []
    for label, translations in senses:
        content = []
        if label:
            content.append({"tag": "div", "data": {"content": "sense-label"}, "content": label})
        content.append({
            "tag": "ul",
            "data": {"content": "glossary"},
            "content": [{"tag": "li", "content": t} for t in translations],
        })
        result.append({"type": "structured-content", "content": {"tag": "div", "content": content}})
    return result


def read_tei(stream, name=""):
    """The header and every row of a TEI file, with its source and target languages: those its text names, else
    those of its file [name] (`slv-eng.tei`)."""
    header = None
    source = target = ""
    rows = []
    for event, element in ET.iterparse(stream, events=("start", "end")):
        if event == "start":
            if element.tag == f"{TEI}body":
                source = element.get(XML_LANG, "")
            continue
        if element.tag == f"{TEI}teiHeader":
            header = read_header(element)
        elif element.tag == f"{TEI}entry":
            if not target:
                cit = element.find(f".//{TEI}cit[@type='trans']")
                target = cit.get(XML_LANG, "") if cit is not None else ""
            rows.extend(read_entry(element, source))
            element.clear()
    if header is None:
        raise ValueError("no TEI header")
    if not (source and target):
        pair = Path(name).stem.split("-")
        if len(pair) != 2 or not all(code in LANGUAGES for code in pair):
            raise ValueError(f"no languages in the text and none in the file name {name!r}")
        source, target = source or LANGUAGES[pair[0]], target or LANGUAGES[pair[1]]
    return header, source, target, rows


def open_tei(path):
    """The TEI file itself, or the one inside a FreeDict source archive (`*.src.tar.xz`), and its file name."""
    path = Path(path)
    if path.suffix == ".tei":
        return path.open("rb"), path.name
    with tarfile.open(path, "r:*") as archive:
        member = next((m for m in archive.getmembers() if m.isfile() and m.name.endswith(".tei")), None)
        if member is None:
            raise ValueError(f"no .tei file in {path.name}")
        return io.BytesIO(archive.extractfile(member).read()), Path(member.name).name


def build(header, source, target, rows):
    """The files of the Yomitan archive, by name."""
    pair = f"{source}-{target}"
    if header["wikdict"]:
        author = ", ".join(f"{name} (WikDict)" for name in header["authors"] or ["Karl Bartel"])
        made = "translations from Wiktionary by WikDict, distributed by FreeDict"
    else:
        author = ", ".join(header["authors"])
        made = "distributed by FreeDict"
    index = {
        "title": f"FreeDict {pair}",
        "format": 3,
        "revision": header["edition"],
        "sequenced": True,
        "author": ", ".join(filter(None, [author, "FreeDict"])),
        "url": "https://freedict.org/",
        "description": f"{header['title']}: {made}. Converted for Yomitan by Screenlate.",
        "attribution": f"{header['license']} {header['license_url']}".strip(),
        "sourceLanguage": source,
        "targetLanguage": target,
    }
    terms = [
        [headword, reading, " ".join(tags), rule, 0, glossary(senses), sequence, ""]
        for sequence, (headword, reading, tags, rule, senses) in enumerate(rows, start=1)
    ]
    used = sorted({tag for _, _, tags, _, _ in rows for tag in tags})
    notes = TAG_NOTES.get(target, {})
    tag_bank = [
        [tag, "partOfSpeech" if tag not in GENDERS else "", 0, notes.get(tag, tag), 0]
        for tag in used
    ]
    files = {"index.json": index, "tag_bank_1.json": tag_bank}
    for number, start in enumerate(range(0, len(terms), TERMS_PER_BANK), start=1):
        files[f"term_bank_{number}.json"] = terms[start:start + TERMS_PER_BANK]
    return files


def write_zip(files, out):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, value in files.items():
            archive.writestr(name, json.dumps(value, ensure_ascii=False, separators=(",", ":")))
        archive.writestr("styles.css", STYLES)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", help="FreeDict source archive (*.src.tar.xz) or its .tei file")
    parser.add_argument("out", help="Yomitan archive to write")
    args = parser.parse_args(argv)
    stream, name = open_tei(args.source)
    with stream:
        header, source, target, rows = read_tei(stream, name)
    files = build(header, source, target, rows)
    write_zip(files, args.out)
    headwords = len({row[0] for row in rows})
    print(f"{files['index.json']['title']} {header['edition']}: {len(rows)} terms, {headwords} headwords, "
          f"{Path(args.out).stat().st_size >> 10} KB")


if __name__ == "__main__":
    sys.exit(main())
