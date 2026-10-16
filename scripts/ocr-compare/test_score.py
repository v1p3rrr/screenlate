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

    def test_a_line_split_into_boxes_reads_left_to_right(self):
        truth = {"regions": [{"box": [0, 0, 300, 20], "text": "один два три"}]}
        # The engine split the line in two; the second box starts a pixel higher.
        result = {"lines": [
            {"box": [0, 1, 90, 20], "words": [word("один", [0, 1, 40, 20]), word("два", [50, 1, 90, 20])]},
            {"box": [100, 0, 140, 19], "words": [word("три", [100, 0, 140, 19])]},
        ]}
        self.assertEqual(score.score_image(truth, result)["edits"], 0)

    def test_arabic_rows_read_right_to_left(self):
        truth = {"regions": [{"box": [0, 0, 200, 20], "text": "كتب الولد"}]}
        # One line split in two boxes, the left one listed first.
        result = {"language": "ar", "lines": [
            {"box": [0, 0, 80, 20], "words": [word("الولد", [0, 0, 80, 20])]},
            {"box": [120, 0, 200, 20], "words": [word("كتب", [120, 0, 200, 20])]},
        ]}
        self.assertEqual(score.score_image(truth, result)["edits"], 0)

    def test_character_boxes_outside_the_word_are_replaced_by_an_even_split(self):
        word_ = {"text": "東京", "box": [900, 200, 950, 300], "chars": [[0, 1904, 0, 1953], [0, 1906, 0, 1952]]}
        boxes = [c["box"] for c in score.characters(word_, vertical=True)]
        self.assertEqual(boxes, [[900, 200, 950, 250], [900, 250, 950, 300]])

    def test_character_boxes_reach_halfway_to_their_neighbours(self):
        word_ = {"text": "気持", "box": [200, 520, 330, 566], "chars": [[205, 521, 286, 565], [294, 523, 329, 563]]}
        boxes = [c["box"] for c in score.characters(word_, vertical=False)]
        self.assertEqual(boxes, [[200, 520, 290, 566], [290, 520, 330, 566]])

    def test_persian_letters_match_their_arabic_look_alikes(self):
        truth = {"regions": [{"box": [0, 0, 100, 20], "text": "کتابی"}]}
        result = {"language": "fa", "lines": [{"box": [0, 0, 100, 20], "words": [word("كتابى", [0, 0, 100, 20])]}]}
        totals = score.score_image(truth, result)
        self.assertEqual((totals["edits"], totals["words_found"]), (0, 1))

    def test_full_width_punctuation_matches_half_width(self):
        truth = {"regions": [{"box": [0, 0, 100, 20], "text": "大雨，街"}]}
        result = {"language": "zh", "lines": [{"box": [0, 0, 100, 20], "words": [word("大雨,街", [0, 0, 100, 20])]}]}
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
