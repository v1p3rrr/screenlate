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
        irish = [e["model"] for e in built if e["sourceLanguage"] == "ga"]
        self.assertEqual(irish, ["paddle-v6", "paddle-latin", "paddle-detector-v6", "paddle-detector-v5"])
        self.assertFalse(any(e["recommended"] for e in built if e["sourceLanguage"] == "ga"))
        gaelic = [e["model"] for e in built if e["sourceLanguage"] == "gd" and e["recommended"]]
        self.assertEqual(sorted(gaelic), ["paddle-latin", "paddle-v6"])
        self.assertNotIn("gd", paddle.RECOMMENDED)
        finnish = [e["model"] for e in built if e["sourceLanguage"] == "fi"]
        self.assertEqual(finnish, ["paddle-v6", "paddle-latin", "paddle-detector-v6", "paddle-detector-v5"])
        self.assertFalse(any(e["recommended"] for e in built if e["sourceLanguage"] == "fi"))
        # Hungarian: the Latin v5 model makes 0.41 points fewer errors than ML Kit, less than the 0.5 the rule asks.
        hungarian = [e["model"] for e in built if e["sourceLanguage"] == "hu"]
        self.assertEqual(hungarian, ["paddle-v6", "paddle-latin", "paddle-detector-v6", "paddle-detector-v5"])
        self.assertFalse(any(e["recommended"] for e in built if e["sourceLanguage"] == "hu"))
        # Estonian: v6 0.43% of the characters wrong against ML Kit's 1.33%; v5 0.80% is marked no more.
        estonian = {e["model"]: e["recommended"] for e in built if e["sourceLanguage"] == "et"}
        self.assertEqual(estonian, {"paddle-v6": True, "paddle-latin": False, "paddle-detector-v6": False,
                                    "paddle-detector-v5": False})
        # Azerbaijani: v6 only, as v5 drops every ə; marked, 1.52% of the characters wrong against ML Kit's 4.99%.
        azerbaijani = {e["model"]: e["recommended"] for e in built if e["sourceLanguage"] == "az"}
        self.assertEqual(azerbaijani, {"paddle-v6": True, "paddle-detector-v6": False})
        # Uzbek: v6 only, unmarked; no model makes a third fewer errors than ML Kit.
        uzbek = {e["model"]: e["recommended"] for e in built if e["sourceLanguage"] == "uz"}
        self.assertEqual(uzbek, {"paddle-v6": False, "paddle-detector-v6": False})
        # Cantonese: v6 only, unmarked; ML Kit's Chinese model reads it with fewer errors.
        cantonese = {e["model"]: e["recommended"] for e in built if e["sourceLanguage"] == "yue"}
        self.assertEqual(cantonese, {"paddle-v6": False, "paddle-detector-v6": False})
        # Bulgarian, Macedonian and Mongolian: the Cyrillic v5 model, marked.
        for language in ("bg", "mk", "mn"):
            models = {e["model"]: e["recommended"] for e in built if e["sourceLanguage"] == language}
            self.assertEqual(models, {"paddle-cyrillic": True, "paddle-detector-v5": False})

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
