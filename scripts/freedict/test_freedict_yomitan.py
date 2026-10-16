import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import freedict_yomitan as converter

SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader xml:lang="en">
    <fileDesc>
      <titleStmt><title>Deutsch-Русский FreeDict+WikDict dictionary</title></titleStmt>
      <editionStmt><edition>2025.11.23</edition></editionStmt>
      <publicationStmt>
        <availability status="free">
          <p>Licensed under the <ref target="https://creativecommons.org/licenses/by-sa/3.0/legalcode">Creative
          Commons Attribution-ShareAlike 3.0 Unported</ref> license</p>
        </availability>
      </publicationStmt>
    </fileDesc>
  </teiHeader>
  <text>
    <body xml:lang="de">
      <entry>
        <form><orth>Annahme</orth><pron>ˈannaːmə</pron></form>
        <gramGrp><pos>n</pos><gen>fem</gen></gramGrp>
        <sense>
          <cit type="trans" xml:lang="ru"><quote>предположение</quote></cit>
          <sense><def>Glaube an einen nicht erwiesenen
            Sachverhalt</def></sense>
        </sense>
        <sense>
          <cit type="trans" xml:lang="ru"><quote>приём</quote><quote>принятие</quote><quote>приём</quote></cit>
        </sense>
      </entry>
      <entry>
        <form><orth>und</orth></form>
        <gramGrp><pos>conjunction</pos></gramGrp>
        <sense><cit type="trans" xml:lang="ru"><quote>и</quote></cit></sense>
      </entry>
      <entry>
        <form><orth>leer</orth></form>
        <gramGrp><pos>adj</pos></gramGrp>
        <sense><sense><def>ohne Inhalt</def></sense></sense>
      </entry>
    </body>
  </text>
</TEI>
"""

CHINESE = """<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader><fileDesc><editionStmt><edition>1</edition></editionStmt></fileDesc></teiHeader>
  <text><body xml:lang="zh">
    <entry>
      <form><orth>心憂</orth><pron>-{-{xīnyōu}-}-</pron><pron>shiniou</pron></form>
      <gramGrp><pos>n</pos></gramGrp>
      <sense><cit type="trans" xml:lang="ru"><quote>печаль</quote></cit></sense>
    </entry>
  </body></text>
</TEI>
"""


def convert(tei):
    header, source, target, rows = converter.read_tei(io.BytesIO(tei.encode("utf-8")))
    return converter.build(header, source, target, rows)


class ConverterTest(unittest.TestCase):
    def test_index_names_the_pair_edition_and_license(self):
        index = convert(SAMPLE)["index.json"]
        self.assertEqual(index["title"], "FreeDict de-ru")
        self.assertEqual(index["revision"], "2025.11.23")
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"]), ("de", "ru"))
        self.assertIn("Creative Commons Attribution-ShareAlike 3.0 Unported", index["attribution"])
        self.assertIn("https://creativecommons.org/licenses/by-sa/3.0/legalcode", index["attribution"])

    def test_each_sense_lists_its_translations_once_under_its_definition(self):
        terms = convert(SAMPLE)["term_bank_1.json"]
        annahme = next(t for t in terms if t[0] == "Annahme")
        self.assertEqual(annahme[2:4], ["n fem", "n"])
        first, second = (g["content"]["content"] for g in annahme[5])
        self.assertEqual(first[0], {
            "tag": "div", "data": {"content": "sense-label"}, "content": "Glaube an einen nicht erwiesenen Sachverhalt",
        })
        self.assertEqual([li["content"] for li in first[1]["content"]], ["предположение"])
        self.assertEqual(len(second), 1)
        self.assertEqual([li["content"] for li in second[0]["content"]], ["приём", "принятие"])

    def test_long_part_of_speech_names_become_short_tags_and_unknown_rules_stay_empty(self):
        terms = convert(SAMPLE)["term_bank_1.json"]
        und = next(t for t in terms if t[0] == "und")
        self.assertEqual(und[2:4], ["conj", ""])

    def test_entries_without_translations_are_left_out(self):
        terms = convert(SAMPLE)["term_bank_1.json"]
        self.assertNotIn("leer", [t[0] for t in terms])

    def test_tags_have_notes_in_the_gloss_language(self):
        tags = {tag[0]: tag for tag in convert(SAMPLE)["tag_bank_1.json"]}
        self.assertEqual(tags["fem"][3], "женский род")
        self.assertEqual(tags["n"][1], "partOfSpeech")
        self.assertEqual(tags["fem"][1], "")

    def test_chinese_headwords_read_as_their_pinyin(self):
        terms = convert(CHINESE)["term_bank_1.json"]
        self.assertEqual(terms[0][:2], ["心憂", "xīnyōu"])

    def test_archive_holds_the_index_banks_and_styles(self):
        files = convert(SAMPLE)
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder) / "out.zip"
            converter.write_zip(files, out)
            with zipfile.ZipFile(out) as archive:
                self.assertEqual(
                    sorted(archive.namelist()),
                    ["index.json", "styles.css", "tag_bank_1.json", "term_bank_1.json"],
                )
                self.assertEqual(json.loads(archive.read("index.json"))["format"], 3)


if __name__ == "__main__":
    unittest.main()
