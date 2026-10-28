"""Tests of the Paddle model archives and catalog entries: python -m unittest discover scripts/ocr-models"""
import hashlib
import unittest

import paddle_models as paddle


class CharactersTest(unittest.TestCase):
    def test_reads_the_character_list_with_quoted_values(self):
        yml = "PostProcess:\n  character_dict:\n  - a\n  - ''''\n  - ' '\n  - \"\u00e9\"\n  name: CTCLabelDecode\n"
        self.assertEqual(paddle.characters(yml), ["a", "'", " ", "\u00e9"])


class EntriesTest(unittest.TestCase):
    archives = {model: model.encode() for model in {**paddle.DETECTORS, **paddle.RECOGNIZERS}}

    def test_each_recognizer_entry_requires_its_detector_which_follows_it(self):
        built = paddle.entries(self.archives)
        for language in ("ja", "en", "ru", "ko"):
            own = [e for e in built if e["sourceLanguage"] == language]
            models = [e["model"] for e in own]
            for e in own:
                for required in e.get("requires", []):
                    self.assertIn(required, models)
                    self.assertGreater(models.index(required), models.index(e["model"]))
        self.assertEqual(len({e["id"] for e in built}), len(built))
        self.assertEqual(len({e["title"] for e in built}), len(built))

    def test_recommends_one_model_per_language_by_the_rule(self):
        built = paddle.entries(self.archives)
        recommended = {e["sourceLanguage"]: e["model"] for e in built
                       if e["recommended"] and e["sourceLanguage"] not in paddle.MORE_ACCURATE}
        self.assertEqual(recommended, paddle.RECOMMENDED)
        self.assertNotIn("ko", recommended)

    def test_marks_every_model_more_accurate_than_ml_kit_where_there_are_several(self):
        built = paddle.entries(self.archives)
        welsh = [e["model"] for e in built if e["sourceLanguage"] == "cy" and e["recommended"]]
        self.assertEqual(sorted(welsh), ["paddle-latin", "paddle-v6"])
        self.assertNotIn("cy", paddle.RECOMMENDED)

    def test_describes_an_archive_with_its_checksum(self):
        entry = paddle.entry("paddle-v6", "ja", "paddle-v6", b"zip", "paddle-detector-v6", True)
        self.assertEqual(entry["sha256"], hashlib.sha256(b"zip").hexdigest())
        self.assertEqual(entry["version"], entry["sha256"][:12])
        self.assertEqual(entry["engine"], "paddle")
        self.assertEqual(entry["requires"], ["paddle-detector-v6"])
        self.assertTrue(entry["downloadUrl"].startswith("https://"))
        self.assertTrue(entry["downloadUrl"].endswith("/paddle-v6.zip"))
        self.assertNotIn("file", entry)


class CatalogTest(unittest.TestCase):
    def test_replaces_paddle_entries_and_takes_the_mark_from_other_models(self):
        document = {"dictionaries": [
            {"id": "a"},
            {"id": "old", "template": "paddle-v5"},
            {"id": "tess-ru", "template": "tesseract-fast", "category": "OCR_MODEL", "sourceLanguage": "ru",
             "recommended": True},
            {"id": "tess-ko", "template": "tesseract-fast", "category": "OCR_MODEL", "sourceLanguage": "ko",
             "recommended": True},
        ]}
        result = paddle.without_recommendation(paddle.replace(document, [{"id": "new"}]), {"ru"})
        self.assertEqual([e["id"] for e in result["dictionaries"]], ["a", "new", "tess-ru", "tess-ko"])
        self.assertFalse(result["dictionaries"][2]["recommended"])
        self.assertTrue(result["dictionaries"][3]["recommended"])


if __name__ == "__main__":
    unittest.main()
