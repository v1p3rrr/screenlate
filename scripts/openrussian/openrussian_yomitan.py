#!/usr/bin/env python3
"""Converts the OpenRussian.org word lists into a Yomitan dictionary archive with stress marks and inflected forms.

    python scripts/openrussian/openrussian_yomitan.py <csv folder> <en|de> <out.zip> --revision 2020-09-09

The folder holds `nouns.csv`, `verbs.csv`, `adjectives.csv` and `others.csv`, tab-separated, as published in
github.com/Badestrand/russian-dictionary; the revision is the date of that export. Each word with a translation into
the chosen language becomes a row written without stress and read with it (`кошка`, `ко́шка`), its translations one
definition per group the source separates with semicolons. Every form of its declension or conjugation table becomes
a form-of row the way Wiktionary dictionaries for Yomitan write them:
`["кошки", "ко́шки", "non-lemma", "", 0, [["ко́шка", ["genitive singular"]]], 0, ""]`.
"""

import argparse
import csv
import json
import re
import sys
import zipfile
from pathlib import Path

FILES = ("nouns", "verbs", "adjectives", "others")

#: The source's stress marks: an apostrophe after the stressed vowel, a backtick after a secondary stress.
ACUTE = chr(0x301)
GRAVE = chr(0x300)

CASES = {
    "nom": "nominative",
    "gen": "genitive",
    "dat": "dative",
    "acc": "accusative",
    "inst": "instrumental",
    "prep": "prepositional",
}
NUMBERS = {"sg": "singular", "pl": "plural"}
GENDER_WORDS = {"m": "masculine", "f": "feminine", "n": "neuter"}
PERSONS = {"1": "first-person", "2": "second-person", "3": "third-person"}

#: Form columns of each file and their form-of tags; `{tense}` is the present or the future by the verb's aspect.
FORM_COLUMNS = {
    "nouns": {f"{n}_{c}": f"{case} {number}" for n, number in NUMBERS.items() for c, case in CASES.items()},
    "verbs": {
        "imperative_sg": "imperative singular",
        "imperative_pl": "imperative plural",
        **{f"past_{g}": f"past {gender} singular" for g, gender in GENDER_WORDS.items()},
        "past_pl": "past plural",
        **{
            f"presfut_{n}{p}": f"{person} {number} {{tense}}"
            for n, number in NUMBERS.items()
            for p, person in PERSONS.items()
        },
    },
    "adjectives": {
        "comparative": "comparative",
        "superlative": "superlative",
        **{f"short_{g}": f"short-form {gender} singular" for g, gender in GENDER_WORDS.items()},
        "short_pl": "short-form plural",
        **{
            f"decl_{g}_{c}": f"{case} {gender} singular"
            for g, gender in GENDER_WORDS.items()
            for c, case in CASES.items()
        },
        **{f"decl_pl_{c}": f"{case} plural" for c, case in CASES.items()},
    },
    "others": {},
}

TENSES = {"imperfective": "present", "perfective": "future"}
PART_OF_SPEECH = {"nouns": "n", "verbs": "v", "adjectives": "adj"}
GENDER_TAGS = {"m": ["masc"], "f": ["fem"], "n": ["neut"], "both": ["masc", "fem"]}
ASPECT_TAGS = {"imperfective": ["impf"], "perfective": ["pf"], "both": ["impf", "pf"]}
FORM_OF = "non-lemma"

#: Tag notes in the gloss language.
TAG_NOTES = {
    "en": {
        "n": "noun",
        "v": "verb",
        "adj": "adjective",
        "masc": "masculine",
        "fem": "feminine",
        "neut": "neuter",
        "impf": "imperfective",
        "pf": "perfective",
        FORM_OF: "inflected form",
    },
    "de": {
        "n": "Substantiv",
        "v": "Verb",
        "adj": "Adjektiv",
        "masc": "maskulin",
        "fem": "feminin",
        "neut": "neutrum",
        "impf": "unvollendet",
        "pf": "vollendet",
        FORM_OF: "flektierte Form",
    },
}

#: The line naming a verb's aspect partner, by the partner's aspect.
PARTNER_LABELS = {
    "en": {"perfective": "perfective", "imperfective": "imperfective"},
    "de": {"perfective": "vollendet", "imperfective": "unvollendet"},
}
OPPOSITE_ASPECT = {"imperfective": "perfective", "perfective": "imperfective"}

LANGUAGE_NAMES = {"en": "English", "de": "German"}

#: Characters besides letters a table cell may hold: stress marks, separators, the rare-form asterisk, brackets.
CELL_MARKS = {"'", "`", ACUTE, GRAVE, ",", ";", "/", " ", "*", "(", ")", "[", "]", "-"}

#: A single word of letters, hyphens allowed between them.
WORD = re.compile(r"^[^\W\d_]+(?:-[^\W\d_]+)*$")

#: The German translations bracket asides with box-drawing corners: `└ seinen / ihren┘`.
CORNERS = re.compile(r"└\s*(.*?)\s*┘")

#: Words of another language left among the translations, by gloss language, dropped where they stand between spaces:
#: Uzbek "gapirish" (to speak) after говорить's "speak, talk".
STRAY_WORDS = {"en": {"gapirish"}}

TERMS_PER_BANK = 10_000


def unique(items):
    seen = set()
    return [item for item in items if item and not (item in seen or seen.add(item))]


def bare(word):
    """The word without stress marks, in the source's notation or as combining accents."""
    return "".join(c for c in word if c not in ("'", "`", ACUTE, GRAVE))


def stressed(word):
    """The word with the source's stress marks as combining accents (ко́шка)."""
    return word.replace("'", ACUTE).replace("`", GRAVE)


def forms(cell):
    """
    The single-word forms of a table cell, stressed. Alternatives are separated by commas, semicolons or slashes;
    the asterisk of a rare form and brackets around an alternative are dropped. A cell with notes or other symbols in
    it (`застону//_устари'`, `серёг=>`, `—`) gives none, and alternatives of several words are left out.
    """
    if any(not (c.isalpha() or c in CELL_MARKS) for c in cell):
        return []
    result = []
    for piece in re.split(r"[,;/]", cell):
        piece = piece.strip().lstrip("*").strip("()[] ")
        if WORD.match(bare(piece)):
            result.append(stressed(piece))
    return unique(result)


def definitions(translations, gloss):
    """One definition per group of translations the source separates with semicolons, without the gloss language's
    stray words."""
    stray = STRAY_WORDS.get(gloss, set())
    result = []
    for group in translations.split(";"):
        group = " ".join(word for word in group.split() if word not in stray)
        group = CORNERS.sub(r"(\1)", group).replace("└", "").replace("┘", "")
        result.append(group.strip(" ,"))
    return unique(result)


def read_rows(folder, gloss):
    """Lemma rows `(headword, reading, tags, definitions)` and form rows `(form, reading, lemma reading, tag)`."""
    lemmas = []
    form_rows = []
    for name in FILES:
        with open(Path(folder) / f"{name}.csv", encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream, delimiter="\t"):
                translations = definitions(row.get(f"translations_{gloss}") or "", gloss)
                headword = bare(row["bare"]).strip()
                if not translations or not headword:
                    continue
                reading = stressed((row.get("accented") or "").strip()) or headword
                tags = [PART_OF_SPEECH[name]] if name in PART_OF_SPEECH else []
                tags += GENDER_TAGS.get(row.get("gender") or "", [])
                aspect = row.get("aspect") or ""
                tags += ASPECT_TAGS.get(aspect, [])
                partners = ", ".join(unique(p.strip() for p in (row.get("partner") or "").split(";")))
                if name == "verbs" and partners and aspect in OPPOSITE_ASPECT:
                    translations.append(f"{PARTNER_LABELS[gloss][OPPOSITE_ASPECT[aspect]]}: {partners}")
                lemmas.append((headword, reading, tags, translations))
                tense = TENSES.get(aspect, "present/future")
                for column, tag in FORM_COLUMNS[name].items():
                    for form in forms(row.get(column) or ""):
                        if bare(form) != headword:
                            form_rows.append((bare(form), form, reading, tag.format(tense=tense)))
    return lemmas, form_rows


def build(lemmas, form_rows, gloss, revision):
    """The files of the Yomitan archive, by name."""
    index = {
        "title": f"OpenRussian ru-{gloss}",
        "format": 3,
        "revision": revision,
        "sequenced": True,
        "author": "OpenRussian.org",
        "url": "https://en.openrussian.org/",
        "description": f"Russian words with stress marks, {LANGUAGE_NAMES[gloss]} translations and inflected forms "
                       f"from the OpenRussian.org dictionary data (export of {revision}). "
                       "Converted for Yomitan by Screenlate.",
        "attribution": "OpenRussian.org, Creative Commons Attribution-ShareAlike 4.0 International "
                       "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceLanguage": "ru",
        "targetLanguage": gloss,
    }
    terms = [
        [headword, reading, " ".join(tags), "", 0, translations, sequence, ""]
        for sequence, (headword, reading, tags, translations) in enumerate(lemmas, start=1)
    ]
    grouped = {}
    for form, reading, lemma, tag in form_rows:
        items = grouped.setdefault((form, reading), [])
        if [lemma, [tag]] not in items:
            items.append([lemma, [tag]])
    terms += [[form, reading, FORM_OF, "", 0, items, 0, ""] for (form, reading), items in grouped.items()]
    used = sorted({tag for _, _, tags, _ in lemmas for tag in tags} | ({FORM_OF} if grouped else set()))
    notes = TAG_NOTES[gloss]
    tag_bank = [
        [tag, "partOfSpeech" if tag in PART_OF_SPEECH.values() else "", 0, notes.get(tag, tag), 0]
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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("folder", help="folder with nouns.csv, verbs.csv, adjectives.csv and others.csv")
    parser.add_argument("gloss", choices=sorted(TAG_NOTES), help="language of the translations")
    parser.add_argument("out", help="Yomitan archive to write")
    parser.add_argument("--revision", required=True, help="date of the export, e.g. 2020-09-09")
    args = parser.parse_args(argv)
    lemmas, form_rows = read_rows(args.folder, args.gloss)
    files = build(lemmas, form_rows, args.gloss, args.revision)
    write_zip(files, args.out)
    forms_count = len({(form, reading) for form, reading, _, _ in form_rows})
    print(f"{files['index.json']['title']} {args.revision}: {len(lemmas)} words, {forms_count} form rows, "
          f"{Path(args.out).stat().st_size >> 10} KB")


if __name__ == "__main__":
    sys.exit(main())
