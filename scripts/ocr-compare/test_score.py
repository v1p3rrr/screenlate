import math
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

    def test_words_glued_by_the_engine_read_as_one(self):
        truth = {"regions": [{
            "box": [0, 0, 200, 20], "text": "도서관은 문을 엽니다.",
            "words": [{"text": "도서관은", "box": [0, 0, 60, 20]}, {"text": "문을", "box": [70, 0, 110, 20]},
                      {"text": "엽니다.", "box": [120, 0, 200, 20]}],
        }]}
        words = [("도서관", "", [0, 0, 45, 20]), ("은", " ", [45, 0, 60, 20]), ("문", "", [70, 0, 95, 20]),
                 ("을", " ", [95, 0, 110, 20]), ("엽니다", "", [120, 0, 190, 20]), (".", "", [190, 0, 200, 20])]
        result = {"language": "ko", "lines": [{"box": [0, 0, 200, 20], "words": [
            {"text": text, "separator": separator, "box": box} for text, separator, box in words]}]}
        totals = score.score_image(truth, result)
        self.assertEqual(totals["edits"], 0)
        self.assertEqual((totals["words_found"], totals["taps_right"]), (3, 3))

    def test_a_turned_line_holds_the_words_inside_its_turned_rectangle(self):
        # A 200 x 20 line turned by 10° about (100, 10); a word of the next line lies in its bounds but not in it.
        a = math.radians(10)

        def turned(x, y):
            dx, dy = x - 100, y - 10
            return [100 + dx * math.cos(a) - dy * math.sin(a), 10 + dx * math.sin(a) + dy * math.cos(a)]

        quad = [turned(0, 0), turned(200, 0), turned(200, 20), turned(0, 20)]
        xs, ys = [p[0] for p in quad], [p[1] for p in quad]
        region = {"box": [min(xs), min(ys), max(xs), max(ys)], "quad": quad, "angle": 10, "text": "один два"}
        truth = {"regions": [region]}

        def at(text, x, y):
            cx, cy = turned(x, y)
            return word(text, [cx - 10, cy - 8, cx + 10, cy + 8])

        # The engine gives the second word first and a word from the line below (30 px down) in the bounds.
        result = {"language": "ru", "lines": [
            {"box": [0, 0, 200, 60], "words": [at("два", 150, 10), at("один", 50, 10), at("три", 20, 40)]},
        ]}
        self.assertTrue(score.inside(truth["regions"][0]["box"], *score.center(at("три", 20, 40)["box"])))
        self.assertEqual(score.score_image(truth, result)["edits"], 0)

    def test_characters_of_a_turned_word_lie_on_its_slope(self):
        # Two lines of four characters turned by 10°; the engine read each line as one word (its bounds).
        a = math.radians(10)

        def quad(top):
            corners = [(0, top), (80, top), (80, top + 20), (0, top + 20)]
            return [[x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)] for x, y in corners]

        def bounds(points):
            return [min(p[0] for p in points), min(p[1] for p in points), max(p[0] for p in points),
                    max(p[1] for p in points)]

        regions = [{"box": bounds(quad(top)), "quad": quad(top), "angle": 10, "text": text}
                   for top, text in [(0, "図書館の"), (24, "二階に新")]]
        result = {"language": "ja", "lines": [
            {"box": bounds(r["quad"]), "words": [word(r["text"], bounds(r["quad"]))]} for r in regions]}
        self.assertEqual(score.score_image({"regions": regions}, result)["edits"], 0)

    def test_a_style_counts_only_its_own_regions(self):
        truth = {"regions": [
            {"box": [0, 0, 100, 20], "text": "один", "style": "photo"},
            {"box": [0, 50, 100, 70], "text": "два", "style": "small"},
        ]}
        result = {"language": "ru", "lines": [{"box": [0, 0, 100, 20], "words": [word("одни", [0, 0, 100, 20])]}]}
        photo = score.score_image(truth, result, style="photo")
        self.assertEqual((photo["edits"], photo["chars"]), (2, 4))
        small = score.score_image(truth, result, style="small")
        self.assertEqual((small["edits"], small["chars"]), (3, 3))

    def test_rows_by_style_and_by_language_and_style(self):
        truth = {"regions": [
            {"box": [0, 0, 100, 20], "text": "один", "style": "photo"},
            {"box": [0, 50, 100, 70], "text": "два", "style": "small"},
        ]}
        result = {"language": "ru", "lines": [{"box": [0, 0, 100, 20], "words": [word("один", [0, 0, 100, 20])]}]}
        rows = score.group_scores(truth, result, "language-style")
        self.assertEqual([(key, totals["edits"]) for key, totals in rows], [("ru/photo", 0), ("ru/small", 3)])
        self.assertEqual([key for key, _ in score.group_scores(truth, result, "style")], ["photo", "small"])
        [(key, totals)] = score.group_scores(truth, result, "language")
        self.assertEqual((key, totals["chars"]), ("ru", 7))


if __name__ == "__main__":
    unittest.main()
