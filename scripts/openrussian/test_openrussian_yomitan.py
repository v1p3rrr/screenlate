import csv
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import openrussian_yomitan as converter

ACUTE = chr(0x301)

NOUNS = [
    {
        "bare": "кошка", "accented": "ко'шка", "translations_en": "cat; pussy", "translations_de": "Katze",
        "gender": "f", "sg_nom": "ко'шка", "sg_gen": "ко'шки", "sg_inst": "ко'шкой//*ко'шкою", "pl_nom": "ко'шки",
        "pl_gen": "ко'шек", "pl_inst": "ко'шками",
    },
    {
        "bare": "серьга", "accented": "серьга'", "translations_en": "earring", "translations_de": "",
        "gender": "f", "pl_gen": "серёг=>", "sg_gen": "серьги'",
    },
    {
        "bare": "сирота", "accented": "сирота'", "translations_en": "orphan", "translations_de": "Waise",
        "gender": "both",
    },
]
VERBS = [
    {
        "bare": "говорить", "accented": "говори'ть", "translations_en": "speak, talk gapirish; say",
        "translations_de": "sprechen, └ seinen / ihren┘ Senf dazugeben", "aspect": "imperfective",
        "partner": "сказать;поговорить", "presfut_sg1": "говорю'", "past_f": "говори'ла",
    },
    {
        "bare": "сказать", "accented": "сказа'ть", "translations_en": "say", "translations_de": "sagen",
        "aspect": "perfective", "partner": "говорить", "presfut_sg1": "скажу'", "imperative_sg": "застону//_устари'",
    },
]
ADJECTIVES = [
    {
        "bare": "межпланетный", "accented": "межплане'тный", "translations_en": "interplanetary",
        "translations_de": "interplanetar", "short_m": "ме`жплане'тен", "decl_f_inst": "межплане'тной,(межплане'тною)",
        "superlative": "[[са'мый]] [[межплане'тный]]", "short_pl": "—",
    },
]
OTHERS = [{"bare": "и", "accented": "и", "translations_en": "and", "translations_de": "und; auch"}]


def write(folder, name, rows):
    columns = ["bare", "accented", "translations_en", "translations_de"]
    columns += sorted({key for row in rows for key in row} - set(columns))
    with open(Path(folder) / f"{name}.csv", "w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, columns, delimiter="\t", restval="")
        writer.writeheader()
        writer.writerows(rows)


def convert(gloss="en"):
    with tempfile.TemporaryDirectory() as folder:
        for name, rows in (("nouns", NOUNS), ("verbs", VERBS), ("adjectives", ADJECTIVES), ("others", OTHERS)):
            write(folder, name, rows)
        lemmas, form_rows = converter.read_rows(folder, gloss)
    return converter.build(lemmas, form_rows, gloss, "2020-09-09")


def terms(files):
    return [term for name, bank in files.items() if name.startswith("term_bank") for term in bank]


def lemma(files, headword):
    return next(t for t in terms(files) if t[0] == headword and t[2] != "non-lemma")


def form(files, headword, reading):
    return next(t for t in terms(files) if t[0] == headword and t[1] == reading and t[2] == "non-lemma")


class ConverterTest(unittest.TestCase):
    def test_headwords_are_written_without_stress_and_read_with_it(self):
        files = convert()
        self.assertEqual(lemma(files, "кошка")[:4], ["кошка", "ко" + ACUTE + "шка", "n fem", ""])
        self.assertEqual(lemma(files, "кошка")[5], ["cat", "pussy"])
        self.assertEqual(lemma(files, "и")[:3], ["и", "и", ""])

    def test_table_forms_become_form_of_rows_pointing_at_the_stressed_lemma(self):
        files = convert()
        cat = "ко" + ACUTE + "шка"
        self.assertEqual(
            form(files, "кошки", "ко" + ACUTE + "шки"),
            ["кошки", "ко" + ACUTE + "шки", "non-lemma", "", 0,
             [[cat, ["genitive singular"]], [cat, ["nominative plural"]]], 0, ""],
        )
        self.assertEqual(form(files, "кошками", "ко" + ACUTE + "шками")[5], [[cat, ["instrumental plural"]]])
        self.assertEqual(form(files, "кошкою", "ко" + ACUTE + "шкою")[5], [[cat, ["instrumental singular"]]])
        self.assertNotIn(["кошка", "non-lemma"], [[t[0], t[2]] for t in terms(files)])

    def test_cells_with_notes_or_several_words_give_no_forms(self):
        files = convert()
        forms = {t[0] for t in terms(files) if t[2] == "non-lemma"}
        self.assertIn("серьги", forms)
        self.assertNotIn("серёг", forms)
        self.assertNotIn("застону", forms)
        self.assertEqual({"межпланетен", "межпланетной", "межпланетною"} & forms, {
            "межпланетен", "межпланетной", "межпланетною",
        })
        self.assertTrue(all(" " not in f and "—" not in f for f in forms))
        self.assertIn(chr(0x300), form(files, "межпланетен", "ме" + chr(0x300) + "жплане" + ACUTE + "тен")[1])

    def test_verb_forms_name_the_present_or_the_future_by_aspect(self):
        files = convert()
        self.assertEqual(form(files, "говорю", "говорю" + ACUTE)[5][0][1], ["first-person singular present"])
        self.assertEqual(form(files, "скажу", "скажу" + ACUTE)[5][0][1], ["first-person singular future"])
        self.assertEqual(form(files, "говорила", "говори" + ACUTE + "ла")[5][0][1], ["past feminine singular"])

    def test_verbs_list_their_aspect_partner(self):
        files = convert()
        self.assertEqual(lemma(files, "говорить")[2], "v impf")
        self.assertEqual(lemma(files, "говорить")[5], ["speak, talk", "say", "perfective: сказать, поговорить"])
        self.assertEqual(lemma(convert("de"), "сказать")[5], ["sagen", "unvollendet: говорить"])

    def test_words_without_a_translation_in_the_gloss_language_are_left_out_with_their_forms(self):
        files = convert("de")
        self.assertNotIn("серьга", [t[0] for t in terms(files)])
        self.assertNotIn("серьги", [t[0] for t in terms(files)])
        self.assertEqual(lemma(files, "и")[5], ["und", "auch"])

    def test_stray_words_of_another_language_are_dropped(self):
        # The source has "speak, talk gapirish": the Uzbek word for speaking.
        self.assertEqual(lemma(convert(), "говорить")[5][0], "speak, talk")
        self.assertEqual(converter.definitions("gapirish", "en"), [])
        self.assertEqual(converter.definitions("gapirish", "de"), ["gapirish"])

    def test_german_asides_in_corners_become_parentheses(self):
        self.assertEqual(lemma(convert("de"), "говорить")[5][0], "sprechen, (seinen / ihren) Senf dazugeben")

    def test_common_gender_has_both_gender_tags_and_tags_have_notes(self):
        files = convert("de")
        self.assertEqual(lemma(files, "сирота")[2], "n masc fem")
        tags = {tag[0]: tag for tag in files["tag_bank_1.json"]}
        self.assertEqual(tags["fem"][3], "feminin")
        self.assertEqual(tags["n"][1], "partOfSpeech")
        self.assertEqual(tags["non-lemma"][3], "flektierte Form")

    def test_index_names_the_source_export_and_license(self):
        index = convert()["index.json"]
        self.assertEqual(index["title"], "OpenRussian ru-en")
        self.assertEqual(index["revision"], "2020-09-09")
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"]), ("ru", "en"))
        self.assertIn("https://creativecommons.org/licenses/by-sa/4.0/", index["attribution"])

    def test_archive_holds_the_index_and_banks(self):
        files = convert()
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder) / "out.zip"
            converter.write_zip(files, out)
            with zipfile.ZipFile(out) as archive:
                self.assertEqual(sorted(archive.namelist()), ["index.json", "tag_bank_1.json", "term_bank_1.json"])
                self.assertEqual(json.loads(archive.read("index.json"))["format"], 3)


if __name__ == "__main__":
    unittest.main()
