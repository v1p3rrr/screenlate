import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import monwn_yomitan as converter

TAB = chr(9)
NEWLINE = chr(10)

MONWN = NEWLINE.join(TAB.join(row) for row in [
    ["# Mongolian Wordnet (MonWN)", "mon", "https://github.com/kbatsuren/monwn", "CC BY 4.0"],
    ["06333653-n", "mon:lemma", "нэр"],
    ["06333653-n", "mon:def 0", "хүн эсвэл  зүйлийг нэрлэдэг"],
    ["06333653-n", "mon:exe 0", "Түүний нэр"],
    ["00001740-a", "mon:lemma", "чадвартай"],
    ["00002098-a", "mon:lemma", "чадвартай"],
    ["01000001-v", "mon:lemma", "нэр"],
    # A note after the word, a Latin look-alike, extra spaces.
    ["14000001-n", "mon:lemma", "бутан /хий/"],
    ["14000002-n", "mon:lemma", " тoс "],
    ["14000003-n", "mon:lemma", "хурган  хав"],
    # Left out: Latin letters, mis-encoded text, two words in one, an unclosed note, no synset in WordNet.
    ["14000004-n", "mon:lemma", "c"],
    ["14000004-n", "mon:lemma", "ñ…ð°ð½"],
    ["14000005-n", "mon:lemma", "хүчтэй тал/чанар"],
    ["14000005-n", "mon:lemma", "явц, чиглэл"],
    ["14000005-n", "mon:lemma", "фаун (эртний бурхан"],
    ["99999999-n", "mon:lemma", "алга"],
]) + NEWLINE

WORDNET = {
    "n": [
        "  1 This software and database is being provided to you, the LICENSEE, by  ",
        "06333653 10 n 01 name 0 001 @ 06333000 n 0000 | a language unit",
        "14000001 27 n 01 butane 0 000 | a gas",
        "14000002 27 n 02 oil 0 vegetable_oil 0 000 | a slippery substance",
        "14000003 05 n 02 poodle 0 poodle_dog 0 000 | a breed",
        "14000004 05 n 01 c 0 000 | a letter",
        "14000005 05 n 01 strength 0 000 | a strong point",
    ],
    "a": [
        "00001740 00 a 01 able 0 000 | having the necessary means",
        "00002098 00 s 02 able(p) 0 capable 0 000 | having the capacity",
    ],
    "v": ["01000001 30 v 01 purify 0 000 | make pure"],
}


def synsets():
    found = {}
    for pos, lines in WORDNET.items():
        found.update(converter.read_wordnet_lines(lines, pos))
    return found


def entries():
    lemmas, definitions = converter.read_monwn(io.StringIO(MONWN))
    return converter.collect(lemmas, definitions, synsets())


class ReadTest(unittest.TestCase):
    def test_monwn_rows_give_lemmas_in_file_order_and_one_definition_per_synset(self):
        lemmas, definitions = converter.read_monwn(io.StringIO(MONWN))
        self.assertEqual(lemmas[:3], [("06333653-n", "нэр"), ("00001740-a", "чадвартай"), ("00002098-a", "чадвартай")])
        self.assertEqual(definitions, {"06333653-n": "хүн эсвэл зүйлийг нэрлэдэг"})

    def test_wordnet_lines_give_english_lemmas_without_adjective_positions_or_underscores(self):
        found = synsets()
        self.assertEqual(found["14000002-n"], ["oil", "vegetable oil"])
        self.assertEqual(found["00002098-a"], ["able", "capable"])
        self.assertNotIn("1-n", found)


class HeadwordTest(unittest.TestCase):
    def test_spaces_notes_and_latin_look_alikes(self):
        self.assertEqual(converter.headword(" тoс "), ("тос", None))
        self.assertEqual(converter.headword("хурган  хав"), ("хурган хав", None))
        self.assertEqual(converter.headword("бутан /хий/"), ("бутан", "хий"))
        self.assertEqual(converter.headword("гэрлэлтийн зар/сүмийн/"), ("гэрлэлтийн зар", "сүмийн"))
        self.assertEqual(converter.headword("бүжгэн жүжигчин (эмэгтэй)"), ("бүжгэн жүжигчин", "эмэгтэй"))
        self.assertEqual(converter.headword("Улаанбаатар"), ("Улаанбаатар", None))
        self.assertEqual(converter.headword("далан-хүдэр"), ("далан-хүдэр", None))

    def test_words_outside_the_alphabet_and_other_punctuation_are_left_out(self):
        for text in ["c", "ñ…ð°ð½", "hэрүүл", "хүчтэй тал/чанар", "явц, чиглэл", "фаун (эртний бурхан",
                     "дээврийн (харанхуй) өрөө", "50% спирт", "&", "/хий/"]:
            self.assertIsNone(converter.headword(text), text)


class CollectTest(unittest.TestCase):
    def test_one_row_per_part_of_speech_with_the_english_lemmas_and_the_mongolian_definition(self):
        found = entries()
        self.assertEqual(found[("нэр", "n")], [{
            "type": "structured-content",
            "content": ["name", {"tag": "div", "content": "хүн эсвэл зүйлийг нэрлэдэг"}],
        }])
        self.assertEqual(found[("нэр", "v")], ["purify"])
        self.assertEqual(found[("чадвартай", "adj")], ["able", "able, capable"])
        self.assertEqual(found[("бутан", "n")], ["(хий) butane"])
        self.assertEqual(found[("тос", "n")], ["oil, vegetable oil"])

    def test_left_out_headwords_and_synsets_missing_from_wordnet_give_no_rows(self):
        words = {word for word, _ in entries()}
        self.assertEqual(words, {"нэр", "чадвартай", "бутан", "тос", "хурган хав"})

    def test_a_synset_with_the_same_english_lemmas_is_shown_once(self):
        lemmas = [("14000001-n", "бутан"), ("14000009-n", "бутан")]
        found = converter.collect(lemmas, {}, {"14000001-n": ["butane"], "14000009-n": ["butane"]})
        self.assertEqual(found[("бутан", "n")], ["butane"])


class BuildTest(unittest.TestCase):
    def test_archive_has_the_index_tags_and_rows_sharing_a_sequence_per_headword(self):
        files = converter.build(entries())
        index = files["index.json"]
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"], index["format"]), ("mn", "en", 3))
        self.assertIn("WordNet 3.0 Copyright 2006 by Princeton University", index["attribution"])
        self.assertIn("CC BY-SA 4.0", index["attribution"])
        self.assertEqual({tag[0] for tag in files["tag_bank_1.json"]}, {"n", "v", "adj", "adv"})
        rows = files["term_bank_1.json"]
        name = [row for row in rows if row[0] == "нэр"]
        self.assertEqual([row[2] for row in name], ["n", "v"])
        self.assertEqual(len({row[6] for row in name}), 1)
        self.assertEqual(len({row[6] for row in rows}), 5)

    def test_written_archive_reads_back(self):
        files = converter.build(entries())
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory, "monwn.zip")
            converter.write_zip(files, out)
            with zipfile.ZipFile(out) as archive:
                self.assertEqual(sorted(archive.namelist()), ["index.json", "tag_bank_1.json", "term_bank_1.json"])
                self.assertEqual(json.loads(archive.read("term_bank_1.json")), files["term_bank_1.json"])

    def test_a_file_other_than_the_pinned_one_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            monwn = Path(directory, "wn-data-mon.tsv")
            monwn.write_text(MONWN, encoding="utf-8")
            with self.assertRaises(SystemExit):
                converter.main([str(monwn), str(monwn), str(Path(directory, "out.zip"))])
            self.assertFalse(Path(directory, "out.zip").exists())


if __name__ == "__main__":
    unittest.main()
