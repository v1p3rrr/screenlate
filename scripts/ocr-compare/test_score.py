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


if __name__ == "__main__":
    unittest.main()
