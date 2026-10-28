"""Tests of the measured sizes: python -m unittest discover scripts/catalog"""
import json
import os
import sqlite3
import tempfile
import unittest

import installed_sizes as sizes

TERM = {"id": "a-en", "kind": "TERM", "indexUrl": "https://example.org/a-en.json", "downloadSize": 1000}
TITLED = {"id": "b-en", "kind": "TERM", "oldTitles": ["B"], "downloadSize": 2000}
FORMS = {"id": "a-forms", "kind": "TERM", "build": "forms", "indexUrl": TERM["indexUrl"], "downloadSize": 1000,
         "installedSize": 1200}


class ListingTest(unittest.TestCase):
    def test_reads_kilobytes_per_directory(self):
        listing = sizes.read_listing(["12\tabc/", "3\tdef", "total"])
        self.assertEqual(listing, {"abc": 12 * 1024, "def": 3 * 1024})


class MatchTest(unittest.TestCase):
    def test_follows_the_index_url_and_the_title_rules_of_the_app(self):
        self.assertTrue(sizes.matches(TERM, "TERM", TERM["indexUrl"], "A [2026-10-01]"))
        self.assertFalse(sizes.matches(TERM, "PITCH", TERM["indexUrl"], "A"))
        self.assertTrue(sizes.matches(TITLED, "TERM", None, "B [2026-10-01]"))
        self.assertFalse(sizes.matches(TITLED, "TERM", None, "B (Russian)"))
        self.assertFalse(sizes.matches(TERM, "TERM", TERM["indexUrl"], "A (forms)"))
        self.assertTrue(sizes.matches(FORMS, "TERM", TERM["indexUrl"], "A (forms)"))


class MeasureTest(unittest.TestCase):
    def test_takes_the_newest_copy_and_leaves_out_entries_with_their_own_size(self):
        rows = [
            (2, "A [2026-10-02]", "TERM", "new", TERM["indexUrl"]),
            (1, "A [2026-09-01]", "TERM", "old", TERM["indexUrl"]),
            (3, "A (forms)", "TERM", "forms", TERM["indexUrl"]),
            (4, "B", "TERM", "gone", None),
        ]
        listing = {"new": 2048, "old": 1024, "forms": 4096}
        measured = sizes.measure([TERM, TITLED, FORMS], rows, listing)
        self.assertEqual(measured, {"a-en": {"download": 1000, "installed": 2048}})

    def test_keeps_earlier_sizes_of_entries_still_in_the_catalog(self):
        with tempfile.TemporaryDirectory() as directory:
            catalog = os.path.join(directory, "catalog.json")
            with open(catalog, "w", encoding="utf-8") as file:
                json.dump({"dictionaries": [TERM, TITLED, FORMS]}, file)
            out = os.path.join(directory, "sizes.json")
            with open(out, "w", encoding="utf-8") as file:
                file.write(sizes.render({"b-en": {"download": 2000, "installed": 5000},
                                         "removed": {"download": 1, "installed": 2}}))
            listing = os.path.join(directory, "du.txt")
            with open(listing, "w", encoding="utf-8") as file:
                file.write("3\tnew/\n")
            db_path = os.path.join(directory, "dictionaries.db")
            db = sqlite3.connect(db_path)
            db.execute("create table dictionaries (id integer, title text, kind text, directory text, indexUrl text)")
            db.execute("insert into dictionaries values (1, 'A', 'TERM', 'new', ?)", (TERM["indexUrl"],))
            db.commit()
            db.close()
            sizes.main([listing, db_path, catalog, out])
            with open(out, encoding="utf-8") as file:
                written = json.load(file)["sizes"]
        self.assertEqual(written, {
            "a-en": {"download": 1000, "installed": 3072},
            "b-en": {"download": 2000, "installed": 5000},
        })


if __name__ == "__main__":
    unittest.main()
