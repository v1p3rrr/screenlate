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
};

class MarkStripper {
 public:
  explicit MarkStripper(const HeadwordMarks& marks);

  // [text] without the marks that follow one of their bases, composed again (NFC); empty when none is dropped.
  std::string strip(std::string_view text) const;

  // The other spellings [text] is found under: without the marks ([strip]), with the replacements, and with both;
  // then without the letters' marks as well, with the replacements made; each once, [text] itself left out.
  std::vector<std::string> keys(std::string_view text) const;

  bool every_headword() const { return every_headword_; }

  bool lemmas_as_written() const { return lemmas_as_written_; }

  // Whether a form-of row of [text] is left out: forms are in the script of the bases and [text] has none of them.
  bool drops_form(std::string_view text) const;

 private:
  using Replacements = std::vector<std::pair<std::string, std::string>>;

  // Marks and the letters, lowercase, they are dropped after; both sorted code points.
  struct MarkSet {
    std::vector<int32_t> marks;
    std::vector<int32_t> bases;
  };

  // [text] with [replacements] made; empty when none applies.
  static std::string replaced(std::string_view text, const Replacements& replacements);

  // [text] without the marks of [sets] that follow one of their bases, composed again; empty when none is dropped.
  static std::string strip(std::string_view text, const std::vector<MarkSet>& sets);

  std::vector<MarkSet> marks_;
  std::vector<MarkSet> letters_;
  std::vector<int32_t> bases_;
  bool every_headword_;
  bool lemmas_as_written_;
  bool forms_in_script_;
  Replacements replacements_;
  Replacements further_replacements_;
};

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
// with [HeadwordMarks::forms_in_script]. Inflection template names among a form's tags (ar-infl-a) are dropped.
// With [forms_only] (the "Word forms" item of a language whose forms only the English Wiktionary dictionary lists),
// the definitions are left out as well: nothing is written to [output], as hoshidicts imports no dictionary without
// entries; the table and [ArchiveSplit::index] make the dictionary, and every row with a form-of item counts as a
// form row.
ArchiveSplit split_form_of(const std::string& archive, const std::string& output,
                           const std::map<std::string, HeadwordMarks>& marks, bool forms_only = false);

}  // namespace screenlate_language
