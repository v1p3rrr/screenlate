"""Tests of the corpus catalog entries: python -m unittest discover scripts/frequency"""
import io
import tarfile
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


def archive(**files):
    """A gzipped tar archive of [files], path to text."""
    out = io.BytesIO()
    with tarfile.open(fileobj=out, mode="w:gz") as tar:
        for name, text in files.items():
            data = text.encode("utf-8")
            info = tarfile.TarInfo(name.replace("__", "/"))
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return out.getvalue()


class LeipzigCheckTest(unittest.TestCase):
    def test_counts_the_lines_of_the_word_list_wherever_it_is(self):
        data = archive(kaz__kaz_sources_txt="x", **{"kaz__kaz-words.txt": "1\t!\t5\n100\tкітап\t3\n"})
        self.assertEqual(catalog.check(data, "leipzig"), 2)

    def test_refuses_an_archive_without_a_word_list(self):
        with self.assertRaises(ValueError):
            catalog.check(archive(kaz__sentences_txt="1\tБұл."), "leipzig")

    def test_refuses_a_line_without_a_count(self):
        with self.assertRaises(ValueError):
            catalog.check(archive(**{"kaz__kaz-words.txt": "1\tкітап\n"}), "leipzig")


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

    def test_the_kyrgyz_entry_is_the_leipzig_archive(self):
        entry = catalog.entry("ky", catalog.CORPORA["ky"], b"x")
        self.assertEqual(entry["id"], "corpus-ky")
        self.assertEqual(entry["oldTitles"], ["Leipzig Corpora Collection (ky)"])
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/kir_newscrawl_2016_100K.tar.gz")

    def test_the_latin_entry_is_the_leipzig_wikipedia_archive(self):
        entry = catalog.entry("la", catalog.CORPORA["la"], b"x")
        self.assertEqual(entry["id"], "corpus-la")
        self.assertEqual(entry["template"], "leipzig-wikipedia")
        self.assertEqual(entry["version"], "2021")
        self.assertEqual(entry["oldTitles"], ["Leipzig Corpora Collection (la)"])
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/lat_wikipedia_2021_100K.tar.gz")

    def test_the_esperanto_entry_is_the_leipzig_web_archive_of_2012(self):
        entry = catalog.entry("eo", catalog.CORPORA["eo"], b"x")
        self.assertEqual(entry["id"], "corpus-eo")
        self.assertEqual(entry["template"], "leipzig-web")
        self.assertEqual(entry["version"], "2012")
        self.assertEqual(entry["oldTitles"], ["Leipzig Corpora Collection (eo)"])
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/epo_web_2012_100K.tar.gz")

    def test_the_albanian_entry_is_the_leipzig_news_archive_of_2020(self):
        entry = catalog.entry("sq", catalog.CORPORA["sq"], b"x")
        self.assertEqual(entry["id"], "corpus-sq")
        self.assertEqual(entry["template"], "leipzig")
        self.assertEqual(entry["version"], "2020")
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/sqi_news_2020_100K.tar.gz")

    def test_the_swahili_entry_is_the_leipzig_news_archive(self):
        entry = catalog.entry("sw", catalog.CORPORA["sw"], b"x")
        self.assertEqual(entry["id"], "corpus-sw")
        self.assertEqual(entry["template"], "leipzig")
        self.assertEqual(entry["version"], "2020")
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/swa_news_2020_30K.tar.gz")

    def test_the_estonian_entry_is_the_leipzig_news_archive_of_2020(self):
        entry = catalog.entry("et", catalog.CORPORA["et"], b"x")
        self.assertEqual(entry["id"], "corpus-et")
        self.assertEqual(entry["template"], "leipzig")
        self.assertEqual(entry["version"], "2020")
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/est_news_2020_100K.tar.gz")

    def test_the_azerbaijani_entry_is_the_smaller_leipzig_news_archive_of_2020(self):
        entry = catalog.entry("az", catalog.CORPORA["az"], b"x")
        self.assertEqual(entry["id"], "corpus-az")
        self.assertEqual(entry["template"], "leipzig")
        self.assertEqual(entry["version"], "2020")
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/aze_news_2020_30K.tar.gz")

    def test_the_kazakh_entry_is_the_leipzig_archive(self):
        entry = catalog.entry("kk", catalog.CORPORA["kk"], b"x")
        self.assertEqual(entry["id"], "corpus-kk")
        self.assertEqual(entry["template"], "leipzig")
        self.assertEqual(entry["oldTitles"], ["Leipzig Corpora Collection (kk)"])
        self.assertEqual(entry["downloadUrl"],
                         "https://downloads.wortschatz-leipzig.de/corpora/kaz_newscrawl_2016_100K.tar.gz")
        self.assertEqual(entry["license"], "CC BY")


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
