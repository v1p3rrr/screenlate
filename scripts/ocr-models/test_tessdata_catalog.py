"""Tests of the Tesseract catalog entries: python -m unittest discover scripts/ocr-models"""
import hashlib
import unittest

import tessdata_catalog as catalog


class EntryTest(unittest.TestCase):
    def test_describes_a_plain_download_kept_where_tesseract_reads_it(self):
        entry = catalog.entry("ru", "rus", "fast", b"lstm")
        self.assertEqual(entry["model"], "tesseract-fast-rus")
        self.assertEqual(entry["engine"], "tesseract")
        self.assertEqual(entry["file"], "tessdata/rus.traineddata")
        self.assertEqual(entry["sha256"], hashlib.sha256(b"lstm").hexdigest())
        self.assertEqual(entry["version"], entry["sha256"][:12])
        self.assertEqual(entry["downloadSize"], 4)
        self.assertTrue(entry["recommended"])
        self.assertIn("/tessdata_fast/", entry["downloadUrl"])
        self.assertFalse(catalog.entry("ru", "rus", "best", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("en", "eng", "fast", b"lstm")["recommended"])


class ReplaceTest(unittest.TestCase):
    def test_replaces_the_old_entries_in_place_and_keeps_the_others(self):
        document = {"dictionaries": [
            {"id": "a"}, {"id": "old-fast", "template": "tesseract-fast"}, {"id": "b"},
            {"id": "old-best", "template": "tesseract-best"},
        ]}
        built = [{"id": "new"}]
        self.assertEqual([e["id"] for e in catalog.replace(document, built)["dictionaries"]], ["a", "new", "b"])

    def test_appends_when_there_were_none(self):
        document = {"dictionaries": [{"id": "a"}]}
        self.assertEqual([e["id"] for e in catalog.replace(document, [{"id": "new"}])["dictionaries"]], ["a", "new"])


if __name__ == "__main__":
    unittest.main()
