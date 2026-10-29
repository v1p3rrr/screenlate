// Splits the form-of rows out of a Yomitan archive before hoshidicts imports it (see form_of_table.hpp).
#pragma once

#include <cstddef>
#include <cstdint>
#include <map>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "form_of_table.hpp"

namespace screenlate_language {

// Combining marks that the words of a language's form-of rows carry and running text leaves out (stress marks), and
// the letters, lowercase, they are dropped after; both as UTF-8 text.
struct HeadwordMarks {
  std::string marks;
  std::string bases;
  // Whether every headword carries them and lemmas name headwords as written (the Greek tonos): a headword with
  // definitions is found without them as well, and lemmas keep them.
  bool every_headword = false;
  // Marks that running text writes as another one, each as UTF-8 text (the Hindi candrabindu as anusvara): a word is
  // also found with them replaced, with its marks and without them.
  std::map<std::string, std::string> replacements;
  // Replacements made on top of [replacements] for one more spelling (the Arabic hamza alefs as a bare alif, after
  // final ى and ة as ي and ه): a word is found with the first ones made and with both.
  std::map<std::string, std::string> further_replacements;
  // Whether lemmas name headwords as written, marks included; otherwise they are stored without the marks.
  bool lemmas_as_written = false;
  // Whether forms are written in the script of [bases] only: a form-of row without one of their letters is dropped
  // (the romanized forms of Wiktionary's Arabic exports, ḡarafāt).
  bool forms_in_script = false;
  // Further marks, each with the letters it is dropped after in place of [bases], both as UTF-8 text: Lithuanian drops
  // the dot above after i (the soft dot of an accented i), not after e (ė).
  std::map<std::string, std::string> mark_bases;
  // Marks of the language's letters (ą č ė), dropped after [letter_bases] once [marks] are, for one more spelling a
  // word is found under: text written without them finds the words with them.
  std::string letter_marks;
  std::string letter_bases;
  // Whether every headword and form is also found without any mark over or under its Latin and Cyrillic letters, with
  // the letters Unicode does not decompose (ø ł ı, ғ қ ө) as their base letters: text written without them finds the
  // words with them, after the words it spells as written.
  bool fold_marks = false;
  // Prefixes of comparative forms (Bulgarian по-, най-): a "comparative" tag of a form-of item whose form has none of
  // them is dropped, as Wiktionary's Bulgarian tables put it on positive forms (мо́ята → мой <comparative>).
  std::vector<std::string> comparative_prefixes;
  // Form headwords left out, as written: labels of Wiktionary's tables that its export gives as forms of every tabled
  // word (Marathi होकारार्थी "affirmative").
  std::vector<std::string> dropped_forms;
  // Word endings that running text writes as others, each as UTF-8 text (the Telugu -ము as -ం): a word is also found
  // with its ending replaced, as written and with the [replacements] made.
  std::map<std::string, std::string> ending_replacements;
};

class MarkStripper {
 public:
  explicit MarkStripper(const HeadwordMarks& marks);

  // [text] without the marks that follow one of their bases, composed again (NFC); empty when none is dropped.
  std::string strip(std::string_view text) const;

  // The other spellings [text] is found under: without the marks ([strip]), with the replacements, and with both;
  // then without the letters' marks as well, with the replacements made; then each of those and [text] with the
  // ending replaced; each once, [text] itself left out.
  std::vector<std::string> keys(std::string_view text) const;

  bool every_headword() const { return every_headword_; }

  // Whether headwords with definitions get keys of their own: they carry the marks, or marks are folded.
  bool keys_headwords() const { return every_headword_ || fold_marks_; }

  // With [HeadwordMarks::fold_marks], [text] and its [keys] folded ([folded]), each once, those that are [text] or
  // among [keys] left out; otherwise none.
  std::vector<std::string> folded_keys(std::string_view text, const std::vector<std::string>& keys) const;

  bool lemmas_as_written() const { return lemmas_as_written_; }

  // Whether a form-of row of [text] is left out: [text] is one of [HeadwordMarks::dropped_forms], or forms are in the
  // script of the bases and [text] has none of them.
  bool drops_form(std::string_view text) const;

  // Whether a "comparative" tag of a form-of item of [form] stays ([HeadwordMarks::comparative_prefixes]).
  bool keeps_comparative(std::string_view form) const;

 private:
  using Replacements = std::vector<std::pair<std::string, std::string>>;

  // Marks and the letters, lowercase, they are dropped after; both sorted code points.
  struct MarkSet {
    std::vector<int32_t> marks;
    std::vector<int32_t> bases;
  };

  // [text] with [replacements] made; empty when none applies.
  static std::string replaced(std::string_view text, const Replacements& replacements);

  // [text] with the first of [ending_replacements_] it ends with replaced; empty when it ends with none.
  std::string ending_replaced(std::string_view text) const;

  // [text] without the marks of [sets] that follow one of their bases, composed again; empty when none is dropped.
  static std::string strip(std::string_view text, const std::vector<MarkSet>& sets);

  std::vector<MarkSet> marks_;
  std::vector<MarkSet> letters_;
  std::vector<int32_t> bases_;
  bool every_headword_;
  bool fold_marks_;
  bool lemmas_as_written_;
  bool forms_in_script_;
  // [HeadwordMarks::comparative_prefixes] lowercase without marks.
  std::vector<std::string> comparative_prefixes_;
  std::vector<std::string> dropped_forms_;
  Replacements replacements_;
  Replacements further_replacements_;
  Replacements ending_replacements_;
};

// [text] without the marks over or under its Latin and Cyrillic letters, those Unicode does not decompose (ø ł ı, ғ қ
// ө) as their base letters, composed again (NFC): fűzfa, fúzfa → fuzfa, Łódź → Lodz, Қазақстан → Казакстан, ёлка →
// елка. Letters of their own without a mark (æ ß ə ŋ, і ә һ) and other scripts stay. Empty when nothing changes.
std::string folded(std::string_view text);

struct ArchiveSplit {
  // Whether [output] holds the archive to import instead of the original: one without the form-of rows.
  bool written = false;
  FormOfTableBuilder table;
  // Rows that held only form-of items and were left out.
  size_t form_rows = 0;
  // Rows that kept their definitions while their form-of items went into the table.
  size_t mixed_rows = 0;
  // With [split_form_of]'s forms_only, the archive's index.json titled with [kFormsOnlyTitleSuffix].
  std::string index;
  // With [split_form_of]'s forms_only, the headwords of the rows with definitions, as written, in the archive's order
  // and with repeats (HeadwordList).
  std::vector<std::string> headwords;
};

// Appended to the index title of a dictionary imported for its forms only ([split_form_of]'s forms_only), so it is
// told apart from the whole dictionary it was cut from.
inline constexpr std::string_view kFormsOnlyTitleSuffix = " (forms)";

// Reads the term banks of the archive at [archive]; when they hold form-of items, writes an archive without them to
// [output] and returns them in the table. Only dictionaries whose index names a source language other than Japanese are
// split: Japanese lookup runs in hoshidicts, which keeps such rows, and a dictionary without a language serves it too.
// Archives this cannot read are left to hoshidicts as well. Where [marks] has the source language, a form that carries
// them is also found without them, and the lemmas it names are stored without them (dictionaries write headwords
// unmarked: Wiktionary's Russian forms name почти́ть for почтить). With [HeadwordMarks::every_headword], a headword
// with definitions is also found without them, as a form of itself with no tags, and lemmas stay as written; the
// table is then returned even when the archive needs no rewriting ([ArchiveSplit::written] false). The
// [HeadwordMarks::replacements] give such a form or headword further spellings the same way; lemmas lose the marks
// unless [HeadwordMarks::lemmas_as_written]. A form written with a romanization or note after it, or with two
// spellings, is keyed by its words; one of an unfilled template by none, and one outside the language's script by none
// with [HeadwordMarks::forms_in_script]. Inflection template names among a form's tags (ar-infl-a) are dropped, and
// so is a gender left after the lemma a form names ("куќа f"). A form-of item whose form is a lone ending of its lemma
// (bg-ru's и and ите for революционер) is left out, and a row that repeats an earlier one word for word is kept once,
// in every term bank this reads.
// With [HeadwordMarks::fold_marks], every headword with definitions and every form is also found under its folded
// spellings ([MarkStripper::folded_keys]) as a respelling, and every term bank is read for them.
// With [forms_only] (the "Word forms" item of a language whose forms only the English Wiktionary dictionary lists),
// the definitions are left out as well: nothing is written to [output], as hoshidicts imports no dictionary without
// entries; the table and [ArchiveSplit::index] make the dictionary, every row with a form-of item counts as a
// form row, and the headwords of the rows with definitions go to [ArchiveSplit::headwords].
ArchiveSplit split_form_of(const std::string& archive, const std::string& output,
                           const std::map<std::string, HeadwordMarks>& marks, bool forms_only = false);

}  // namespace screenlate_language
