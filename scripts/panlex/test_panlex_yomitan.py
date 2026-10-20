import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import panlex_yomitan as converter

TAB = chr(9)
NEWLINE = chr(10)
STRESS = chr(0x301)
HEADER = ["id", "langvar", "txt", "txt_degr", "meaning", "langvar_uid"]


def tsv(rows):
    """A mirror file of `(text, meaning, variety)` rows."""
    lines = [TAB.join(HEADER)]
    for number, (text, meaning, variety) in enumerate(rows, start=1):
        lines.append(TAB.join([str(number), "1", text, text, str(meaning), variety]))
    return NEWLINE.join(lines) + NEWLINE


KYRGYZ = [
    ("кол", 1, "kir-000"),
    ("кол", 2, "kir-000"),
    ("кол", 3, "kir-000"),
    ("kol", 1, "kir-002"),
    ("кол", 9, "kir-001"),
    # Kazakh letters, a capital, Latin letters and punctuation are no Kyrgyz headwords.
    ("қол", 1, "kir-000"),
    ("Бишкек", 4, "kir-000"),
    ("сЛ", 4, "kir-000"),
    ("TJS", 4, "kir-000"),
    (",", 4, "kir-000"),
    ("баскычы", 5, "kir-000"),
    # Five headwords share meaning 6: a loose group.
    ("ага", 6, "kir-000"),
    ("дос", 6, "kir-000"),
    ("ини", 6, "kir-000"),
    ("тууган", 6, "kir-000"),
    ("жолдош", 6, "kir-000"),
    ("ага", 7, "kir-000"),
    ("Оруссия", 8, "kir-000"),
    ("өлкө", 8, "kir-000"),
]

RUSSIAN = [
    ("рука", 1, "rus-000"),
    ("ладонь", 1, "rus-000"),
    ("рука" + STRESS, 2, "rus-000"),
    ("подпись", 3, "rus-000"),
    ("рука", 3, "rus-000"),
    ("ruka", 1, "rus-001"),
    ("лапа", 9, "rus-000"),
    ("Бишкек", 4, "rus-000"),
    ("клавиша PAGE DOWN", 5, "rus-000"),
    ("брат", 6, "rus-000"),
    ("старший брат", 7, "rus-000"),
] + [(f"город{number}", 8, "rus-000") for number in range(21)]

ENGLISH = [
    ("hand", 1, "eng-000"),
    ("рука", 1, "eng-000"),
    ("arm", 2, "eng-000"),
    ("hand", 3, "eng-000"),
    ("signature", 3, "eng-000"),
]


class PanLexTest(unittest.TestCase):

    def setUp(self):
        self.folder = Path(tempfile.mkdtemp())
        for name, rows in (("kir", KYRGYZ), ("rus", RUSSIAN), ("eng", ENGLISH)):
            (self.folder / f"{name}.tsv").write_text(tsv(rows), encoding="utf-8")

    def entries(self, target):
        files = converter.convert(self.folder / "kir.tsv", self.folder / f"{target}.tsv")
        return {row[0]: row[5][0] for row in files["term_bank_1.json"]}, files

    def test_translations_come_in_the_order_of_their_support_and_stress_marks_are_one_spelling(self):
        entries, _ = self.entries("rus")
        # рука in meanings 1, 2 and 3, shown with its stress mark; ладонь and подпись once each, in file order.
        self.assertEqual(entries["кол"], "рука" + STRESS + ", ладонь, подпись")

    def test_only_lowercase_headwords_in_the_languages_own_letters_and_only_standard_varieties(self):
        entries, _ = self.entries("rus")
        for word in ("қол", "Бишкек", "сЛ", "TJS", ",", "kol"):
            self.assertNotIn(word, entries)
        self.assertNotIn("лапа", entries["кол"])

    def test_loose_groups_and_lists_are_left_out(self):
        entries, _ = self.entries("rus")
        self.assertEqual(entries["ага"], "старший брат")
        for word in ("дос", "ини", "тууган", "жолдош", "өлкө"):
            self.assertNotIn(word, entries)

    def test_translations_in_another_script_are_left_out(self):
        russian, _ = self.entries("rus")
        self.assertNotIn("баскычы", russian)
        english, _ = self.entries("eng")
        self.assertEqual(english["кол"], "hand, arm, signature")

    def test_a_headword_shows_ten_translations_at_most(self):
        rows = [("сөз", 1, "kir-000")]
        translations = [(f"слово{chr(0x430 + number)}", 1, "rus-000") for number in range(12)]
        (self.folder / "kir.tsv").write_text(tsv(rows), encoding="utf-8")
        (self.folder / "rus.tsv").write_text(tsv(translations), encoding="utf-8")
        entries, _ = self.entries("rus")
        self.assertEqual(len(entries["сөз"].split(", ")), converter.MAX_TRANSLATIONS)

    def test_the_index_names_the_pair_the_snapshot_and_its_license(self):
        _, files = self.entries("rus")
        index = files["index.json"]
        self.assertEqual(index["title"], "PanLex ky-ru")
        self.assertEqual(index["revision"], "2024-03-01")
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"]), ("ky", "ru"))
        self.assertIn("CC0 1.0", index["attribution"])
        self.assertIn(converter.MIRROR_REVISION[:12], index["attribution"])

    def test_rows_are_yomitan_terms_without_tags_and_the_archive_is_reproducible(self):
        _, files = self.entries("rus")
        row = next(row for row in files["term_bank_1.json"] if row[0] == "кол")
        self.assertEqual(row[1:5], ["", "", "", 0])
        self.assertEqual(row[7], "")
        first, second = self.folder / "a.zip", self.folder / "b.zip"
        converter.write_zip(files, first)
        converter.write_zip(files, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertEqual(json.loads(archive.read("index.json"))["title"], "PanLex ky-ru")

    def test_a_file_must_be_named_by_a_known_language_code(self):
        with self.assertRaises(ValueError):
            converter.variety_of(self.folder / "xyz.tsv")
        self.assertEqual(converter.variety_of(self.folder / "kaz.tsv"), ("kaz-000", "kk"))

    def test_kazakh_headwords_take_the_kazakh_letters(self):
        self.assertTrue(converter.is_headword("қол", "kk"))
        self.assertTrue(converter.is_headword("білім беру", "kk"))
        self.assertFalse(converter.is_headword("бірегей", "ky"))
        self.assertTrue(converter.is_headword("көл", "ky"))


if __name__ == "__main__":
    unittest.main()
