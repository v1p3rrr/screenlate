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
};

class MarkStripper {
 public:
  explicit MarkStripper(const HeadwordMarks& marks);

  // [text] without the marks that follow one of the bases, composed again (NFC); empty when none is dropped.
  std::string strip(std::string_view text) const;

  // The other spellings [text] is found under: without the marks ([strip]), with the replacements, and with both;
  // each once, [text] itself left out.
  std::vector<std::string> keys(std::string_view text) const;

  bool every_headword() const { return every_headword_; }

 private:
  // [text] with the replacements made; empty when none applies.
  std::string replaced(std::string_view text) const;

  std::vector<int32_t> marks_;
  std::vector<int32_t> bases_;
  bool every_headword_;
  std::vector<std::pair<std::string, std::string>> replacements_;
};

struct ArchiveSplit {
  // Whether [output] holds the archive to import instead of the original: one without the form-of rows.
  bool written = false;
  FormOfTableBuilder table;
  // Rows that held only form-of items and were left out.
  size_t form_rows = 0;
  // Rows that kept their definitions while their form-of items went into the table.
  size_t mixed_rows = 0;
};

// Reads the term banks of the archive at [archive]; when they hold form-of items, writes an archive without them to
// [output] and returns them in the table. Only dictionaries whose index names a source language other than Japanese are
// split: Japanese lookup runs in hoshidicts, which keeps such rows, and a dictionary without a language serves it too.
// Archives this cannot read are left to hoshidicts as well. Where [marks] has the source language, a form that carries
// them is also found without them, and the lemmas it names are stored without them (dictionaries write headwords
// unmarked: Wiktionary's Russian forms name почти́ть for почтить). With [HeadwordMarks::every_headword], a headword
// with definitions is also found without them, as a form of itself with no tags, and lemmas stay as written; the
// table is then returned even when the archive needs no rewriting ([ArchiveSplit::written] false). The
// [HeadwordMarks::replacements] give such a form or headword further spellings the same way.
ArchiveSplit split_form_of(const std::string& archive, const std::string& output,
                           const std::map<std::string, HeadwordMarks>& marks);

}  // namespace screenlate_language
