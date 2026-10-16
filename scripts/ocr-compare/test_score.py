import unittest

import score

TRUTH = {
    "regions": [
        {
            "box": [0, 0, 200, 20],
            "text": "Кошки сидели на окне.",
            "words": [
                {"text": "Кошки", "box": [0, 0, 50, 20]},
                {"text": "сидели", "box": [55, 0, 110, 20]},
                {"text": "на", "box": [115, 0, 135, 20]},
                {"text": "окне.", "box": [140, 0, 200, 20]},
            ],
        },
        {"box": [0, 100, 100, 120], "text": "Настройки"},
    ]
}


def word(text, box):
    return {"text": text, "box": box}


class ScoreTest(unittest.TestCase):
    def test_a_perfect_read_has_no_errors(self):
        result = {"lines": [
            {"box": [0, 0, 200, 20], "words": [word(w["text"], w["box"]) for w in TRUTH["regions"][0]["words"]]},
            {"box": [0, 100, 100, 120], "words": [word("Настройки", [0, 100, 100, 120])]},
        ]}
        totals = score.score_image(TRUTH, result)
        self.assertEqual(totals["edits"], 0)
        self.assertEqual(totals["chars"], len("Кошки сидели на окне.") + len("Настройки"))
        self.assertEqual((totals["words_found"], totals["words"]), (5, 5))
        self.assertEqual((totals["taps_right"], totals["taps"]), (4, 4))

    def test_misreads_merged_words_and_missed_regions_count(self):
        result = {"lines": [
            {"box": [0, 0, 200, 20], "words": [word("Кошкн", [0, 0, 50, 20]), word("сиделина", [55, 0, 135, 20]),
                                               word("окне", [140, 0, 200, 20])]},
        ]}
        totals = score.score_image(TRUTH, result)
        # "Кошкн сиделина окне" against "Кошки сидели на окне.": и→н, a space, the full stop.
        self.assertEqual(totals["edits"], 3 + len("Настройки"))
        self.assertEqual(totals["words_found"], 1)
        self.assertEqual(totals["taps_right"], 1)

    def test_words_are_read_in_line_order_inside_a_region(self):
        truth = {"regions": [{"box": [0, 0, 100, 50], "text": "один два"}]}
        result = {"lines": [
            {"box": [0, 30, 100, 50], "words": [word("два", [0, 30, 40, 50])]},
            {"box": [0, 0, 100, 20], "words": [word("один", [0, 0, 40, 20])]},
        ]}
        self.assertEqual(score.score_image(truth, result)["edits"], 0)

    def test_japanese_is_scored_by_characters_without_spaces(self):
        truth = {"regions": [{
            "box": [0, 0, 60, 20], "text": "猫が寝た",
            "words": [{"text": c, "box": [i * 15, 0, i * 15 + 15, 20]} for i, c in enumerate("猫が寝た")],
        }]}
        # One word for the whole line, cut evenly; a space the engine put in does not count.
        result = {"language": "ja", "lines": [
            {"box": [0, 0, 60, 20], "words": [word("猫が 寝た", [0, 0, 60, 20])]},
        ]}
        totals = score.score_image(truth, result)
        self.assertEqual(totals["edits"], 0)
        self.assertEqual(totals["chars"], 4)
        self.assertEqual(totals["words"], 0)
        self.assertEqual((totals["taps_right"], totals["taps"]), (4, 4))

    def test_character_boxes_of_the_engine_decide_the_taps(self):
        truth = {"regions": [{
            "box": [0, 0, 40, 20], "text": "日本",
            "words": [{"text": "日", "box": [0, 0, 10, 20]}, {"text": "本", "box": [10, 0, 40, 20]}],
        }]}
        line = {"box": [0, 0, 40, 20], "words": [
            {"text": "日本", "box": [0, 0, 40, 20], "chars": [[0, 0, 10, 20], [10, 0, 40, 20]]},
        ]}
        totals = score.score_image(truth, {"language": "zh", "lines": [line]})
        self.assertEqual((totals["taps_right"], totals["taps"]), (2, 2))

    def test_columns_read_top_to_bottom(self):
        truth = {"regions": [{"box": [0, 0, 20, 60], "text": "上中下"}]}
        result = {"language": "javert", "lines": [
            {"box": [0, 40, 20, 60], "vertical": True, "words": [word("下", [0, 40, 20, 60])]},
            {"box": [1, 0, 20, 40], "vertical": True, "words": [word("上中", [1, 0, 20, 40])]},
        ]}
        self.assertEqual(score.score_image(truth, result)["edits"], 0)


if __name__ == "__main__":
    unittest.main()
