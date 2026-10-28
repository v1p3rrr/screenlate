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
      <sourceDesc>
        <p>Automatic creation of this bilingual dictionary by <ref target="http://www.wikdict.com/">WikDict</ref>.</p>
      </sourceDesc>
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


OLD_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader>
    <fileDesc>
      <titleStmt>
        <title>Slovenian-English FreeDict Dictionary</title>
        <respStmt><resp>Created by</resp><name>Gorazd Gorup</name></respStmt>
      </titleStmt>
      <editionStmt><edition>0.1</edition></editionStmt>
      <publicationStmt>
        <availability status="free">
          <p>Copyright (C) by various authors listed in revisionDesc below.</p>
          <p>Available under the terms of the
            <ref target="http://www.gnu.org/licenses/gpl.html">GNU General Public License</ref>
            ver. 3.0 or any later version.</p>
          <p>This program is free software; you can redistribute it and/or modify it.</p>
        </availability>
      </publicationStmt>
      <sourceDesc><p>Written by a native Slovenian speaker for FreeDict project.</p></sourceDesc>
    </fileDesc>
  </teiHeader>
  <text>
    <body>
      <entry>
        <form><orth>abeceda</orth></form>
        <gramGrp><pos>n</pos><gen>f</gen><num>sg</num></gramGrp>
        <sense>
          <cit type="trans"><quote>alphabet</quote></cit>
          <cit type="example">
            <quote>Slovenska abeceda ima 25 črk.</quote>
            <cit type="trans"><quote>The Slovenian alphabet has 25 letters.</quote></cit>
          </cit>
        </sense>
      </entry>
      <entry>
        <form><orth>abeceden</orth></form>
        <gramGrp><pos>adj</pos><gen>m</gen><num>sg</num></gramGrp>
        <sense><cit type="trans"><quote>alphabetical</quote></cit></sense>
      </entry>
      <entry>
        <form><orth>aha</orth></form>
        <gramGrp><pos>int</pos></gramGrp>
        <sense><cit type="trans"><quote>aha</quote></cit></sense>
      </entry>
    </body>
  </text>
</TEI>
"""


def convert(tei, name=""):
    header, source, target, rows = converter.read_tei(io.BytesIO(tei.encode("utf-8")), name)
    return converter.build(header, source, target, rows)


class ConverterTest(unittest.TestCase):
    def test_index_names_the_pair_edition_and_license(self):
        index = convert(SAMPLE)["index.json"]
        self.assertEqual(index["title"], "FreeDict de-ru")
        self.assertEqual(index["revision"], "2025.11.23")
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"]), ("de", "ru"))
        self.assertIn("Creative Commons Attribution-ShareAlike 3.0 Unported", index["attribution"])
        self.assertIn("https://creativecommons.org/licenses/by-sa/3.0/legalcode", index["attribution"])

    def test_wikdict_files_credit_wikdict(self):
        index = convert(SAMPLE)["index.json"]
        self.assertEqual(index["author"], "Karl Bartel (WikDict), FreeDict")
        self.assertIn("translations from Wiktionary by WikDict", index["description"])

    def test_an_old_header_takes_the_languages_of_the_file_name_and_its_own_authors(self):
        index = convert(OLD_HEADER, "slv-eng.tei")["index.json"]
        self.assertEqual(index["title"], "FreeDict sl-en")
        self.assertEqual((index["sourceLanguage"], index["targetLanguage"]), ("sl", "en"))
        self.assertEqual(index["author"], "Gorazd Gorup, FreeDict")
        self.assertEqual(index["description"],
                         "Slovenian-English FreeDict Dictionary: distributed by FreeDict. Converted for Yomitan by Screenlate.")
        # The license ends with the paragraph that links it.
        self.assertEqual(index["attribution"], "Copyright (C) by various authors listed in revisionDesc below. Available "
                         "under the terms of the GNU General Public License ver. 3.0 or any later version. "
                         "http://www.gnu.org/licenses/gpl.html")

    def test_old_genders_and_parts_of_speech_become_tags_and_examples_are_left_out(self):
        terms = {t[0]: t for t in convert(OLD_HEADER, "slv-eng.tei")["term_bank_1.json"]}
        self.assertEqual(terms["abeceda"][2:4], ["n fem", "n"])
        self.assertEqual([li["content"] for li in terms["abeceda"][5][0]["content"]["content"][0]["content"]], ["alphabet"])
        # An adjective's gender is that of the form written.
        self.assertEqual(terms["abeceden"][2:4], ["adj", "adj"])
        self.assertEqual(terms["aha"][2], "intj")

    def test_a_file_without_languages_needs_a_name_that_gives_them(self):
        with self.assertRaises(ValueError):
            convert(OLD_HEADER, "dictionary.tei")

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

    def test_a_long_definition_follows_the_translations_of_its_sense(self):
        short = "x" * converter.LONG_LABEL
        long = "x" * (converter.LONG_LABEL + 1)
        first, second = (g["content"]["content"] for g in converter.glossary([(short, ["а"]), (long, ["б"])]))
        self.assertEqual([part["data"]["content"] for part in first], ["sense-label", "glossary"])
        self.assertEqual([part["data"]["content"] for part in second], ["glossary", "sense-definition"])
        self.assertEqual(second[1]["content"], long)

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

    def test_definitions_lose_wikdict_link_markup(self):
        self.assertEqual(
            converter.plain("#{finansiell|finansiell}# #{institusjon}# som #{låne|låner}# penger"),
            "finansiell institusjon som låner penger",
        )
        self.assertEqual(converter.plain("betegner at noe #{likne|likner}# på noe, [[minne"),
                         "betegner at noe likner på noe, minne")
        self.assertEqual(converter.plain("urteplante, 30&ndash;50&nbsp;cm høy"), "urteplante, 30–50 cm høy")

    def test_definitions_lose_the_italian_links_to_sister_projects(self):
        self.assertEqual(converter.plain("derivato dai fiori dell'assenzio ( approfondimento)"),
                         "derivato dai fiori dell'assenzio")
        self.assertEqual(converter.plain("(araldica) punta ( approfondimento):"), "(araldica) punta:")
        self.assertEqual(converter.plain("è Acer campestre ( tassonomia), è Acer opalus ( tassonomia)"),
                         "è Acer campestre, è Acer opalus")
        self.assertEqual(converter.plain("casa ( citazioni)"), "casa")
        # The word itself and other parentheses stay.
        self.assertEqual(converter.plain("mancanza di approfondimento ( di clima)"),
                         "mancanza di approfondimento ( di clima)")

    def test_definitions_that_are_only_a_broken_template_are_dropped(self):
        self.assertEqual(converter.plain("}#|utellelig|kat"), "")
        self.assertEqual(converter.plain("Verb|minner]] om noe"), "")

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
