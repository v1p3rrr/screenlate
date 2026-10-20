"""Tests of the corpus catalog entries: python -m unittest discover scripts/frequency"""
import unittest

import corpus_catalog as catalog


class CheckTest(unittest.TestCase):
    def test_counts_lines_of_words_and_counts_sorted_by_count(self):
        self.assertEqual(catalog.check("ที่\t10\n เป็น\t5\nห - ม - า\t5\n".encode("utf-8")), 3)

    def test_refuses_a_line_without_a_count(self):
        with self.assertRaises(ValueError):
            catalog.check("ที่\t10\nการ\n".encode("utf-8"))

    def test_refuses_an_unsorted_list(self):
        with self.assertRaises(ValueError):
            catalog.check("ที่\t10\nการ\t11\n".encode("utf-8"))


class EntryTest(unittest.TestCase):
    def test_the_thai_entry_is_a_recommended_frequency_build(self):
        data = "ที่\t10\n".encode("utf-8")
        entry = catalog.entry("th", catalog.CORPORA["th"], data)
        self.assertEqual(entry["id"], "corpus-th")
        self.assertEqual(entry["build"], "corpus-counts")
        self.assertEqual(entry["category"], "FREQUENCY")
        self.assertTrue(entry["recommended"])
        self.assertEqual(entry["downloadSize"], len(data))
        self.assertEqual(entry["oldTitles"], ["Thai National Corpus (th)"])
        self.assertIn(catalog.PYTHAINLP_COMMIT, entry["downloadUrl"])


class ReplaceTest(unittest.TestCase):
    def test_corpus_entries_go_after_wordfreq_and_replace_the_old_ones(self):
        document = {"dictionaries": [
            {"id": "jitendex"}, {"id": "wordfreq-en", "template": "wordfreq"}, {"id": "corpus-th", "template": "tnc"},
            {"id": "wordfreq-bn", "template": "wordfreq"}, {"id": "wiktionary-en-en", "template": "wiktionary"},
        ]}
        replaced = catalog.replace(document, [{"id": "corpus-th", "template": "tnc", "new": True}])
        self.assertEqual([e["id"] for e in replaced["dictionaries"]],
                         ["jitendex", "wordfreq-en", "wordfreq-bn", "corpus-th", "wiktionary-en-en"])
        self.assertTrue(replaced["dictionaries"][3]["new"])


if __name__ == "__main__":
    unittest.main()
