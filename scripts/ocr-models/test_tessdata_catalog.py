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
        self.assertFalse(entry["recommended"])
        self.assertIn("/tessdata_fast/", entry["downloadUrl"])
        self.assertFalse(catalog.entry("ru", "rus", "best", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("en", "eng", "fast", b"lstm")["recommended"])
        # Nothing else in the catalog reads Thai on the device; best is no better than fast.
        self.assertTrue(catalog.entry("th", "tha", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("th", "tha", "best", b"lstm")["recommended"])
        # Khmer as well: best reads it 37.9% wrong against fast's 38.3%.
        self.assertTrue(catalog.entry("km", "khm", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("km", "khm", "best", b"lstm")["recommended"])
        # Arabic until Paddle's model is in the catalog: best reads it 5.9% wrong against fast's 5.8%.
        self.assertTrue(catalog.entry("ar", "ara", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("ar", "ara", "best", b"lstm")["recommended"])
        # Hebrew: both read clean screens without an error, best hard ones 43.7% wrong against fast's 48.5%.
        self.assertTrue(catalog.entry("he", "heb", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("he", "heb", "best", b"lstm")["recommended"])
        # Welsh, which ML Kit reads: both are marked more accurate than ML Kit, 0.00% against 2.19%.
        self.assertTrue(catalog.entry("cy", "cym", "fast", b"lstm")["recommended"])
        self.assertTrue(catalog.entry("cy", "cym", "best", b"lstm")["recommended"])
        # Irish: ML Kit reads it 0.45% wrong, too few errors left for a mark.
        self.assertFalse(catalog.entry("ga", "gle", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("ga", "gle", "best", b"lstm")["recommended"])
        # Scottish Gaelic: 0.29% against ML Kit's 0.65%, less than 0.5 points fewer; Paddle is marked instead.
        self.assertFalse(catalog.entry("gd", "gla", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("gd", "gla", "best", b"lstm")["recommended"])
        self.assertEqual(catalog.LANGUAGES["gd"], "gla")
        # Finnish: ML Kit reads it with few errors, so neither is marked.
        self.assertFalse(catalog.entry("fi", "fin", "fast", b"lstm")["recommended"])
        self.assertFalse(catalog.entry("fi", "fin", "best", b"lstm")["recommended"])
        self.assertEqual(catalog.LANGUAGES["fi"], "fin")


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
