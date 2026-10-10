"""Tests of the frequency dictionary builder: python -m unittest discover scripts/frequency"""
import json
import os
import tempfile
import unittest
import zipfile

import wordfreq_dictionary as builder


class TermMetaTest(unittest.TestCase):
    def test_ranks_words_with_letters_and_adds_capitalized_forms(self):
        rows = builder.term_meta(["the", ",", "haus", "42", "the", "é", "日本"], size=10)
        self.assertEqual(rows, [
            ["the", "freq", 1], ["The", "freq", 1],
            ["haus", "freq", 2], ["Haus", "freq", 2],
            ["é", "freq", 3], ["É", "freq", 3],
            ["日本", "freq", 4],
        ])

    def test_stops_at_the_size_and_can_leave_out_capitalized_forms(self):
        rows = builder.term_meta(["a", "b", "c"], size=2, with_capitalized=False)
        self.assertEqual(rows, [["a", "freq", 1], ["b", "freq", 2]])


class ArchiveTest(unittest.TestCase):
    def test_writes_a_rank_based_dictionary_with_its_attribution(self):
        rows = [[f"w{i}", "freq", i + 1] for i in range(builder.BANK_SIZE + 5)]
        index = builder.index("en", "3.1.1", len(rows), "https://example.org/en.zip", "https://example.org/en.json")
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "en.zip")
            builder.write(path, index, rows)
            with zipfile.ZipFile(path) as archive:
                self.assertEqual(sorted(archive.namelist()), ["index.json", "term_meta_bank_1.json", "term_meta_bank_2.json"])
                written = json.loads(archive.read("index.json"))
                second = json.loads(archive.read("term_meta_bank_2.json"))
        self.assertEqual(written["frequencyMode"], "rank-based")
        self.assertEqual(written["sourceLanguage"], "en")
        self.assertTrue(written["isUpdatable"])
        self.assertIn("CC BY-SA 4.0", written["attribution"])
        self.assertIn("SUBTLEX", written["attribution"])
        self.assertEqual(len(second), 5)


if __name__ == "__main__":
    unittest.main()
