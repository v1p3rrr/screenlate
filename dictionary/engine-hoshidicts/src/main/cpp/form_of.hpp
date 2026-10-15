// Form-of entries in Japanese lookups. A glossary item `["lemma", ["tag", ...]]` marks its row as a form of another
// term: Wiktionary's "non-lemma" rows (じどう → 自動 "transliteration", 書か → 書く "imperfective godan", 社會 → 社会
// "kyūjitai") hold nothing else. hoshidicts keeps such items in the glossary; Yomitan's dictionary deinflection drops
// them from the definitions and looks the lemma up instead, which this does after hoshidicts' lookup.
#pragma once

#include <string>
#include <vector>

#include "hoshidicts/lookup.hpp"
#include "hoshidicts/query.hpp"

namespace screenlate_form_of {

struct FormOfItem {
  std::string lemma;
  std::vector<std::string> tags;
};

// Removes the form-of items from [glossary], a JSON array, and appends them to [items]. Returns the rest of the array,
// or an empty string when nothing remains.
std::string strip_form_of(const std::string& glossary, std::vector<FormOfItem>& items);

// [results] with their form-of items followed one step, as Yomitan: the items leave the glossaries, a result left
// without definitions goes, and the terms its lemmas name follow it with the matched text, the rule chain plus the
// item's tags. A term already among the results keeps its place and is replaced only by a better match (longer matched
// text, fewer text normalizations, shorter chain), so a word found by its reading or by deinflection stays as it was.
std::vector<LookupResult> follow_form_of(const DictionaryQuery& query, std::vector<LookupResult> results);

}  // namespace screenlate_form_of
