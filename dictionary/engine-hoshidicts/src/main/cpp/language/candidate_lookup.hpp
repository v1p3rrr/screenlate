// Lookup for languages other than Japanese: Yomitan's language code (yomitan_language.hpp) turns the scanned text
// into deinflection candidates, hoshidicts answers exact queries for them, and form-of entries lead to their lemmas as
// Yomitan's dictionary deinflection does.
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "form_of_table.hpp"
#include "hoshidicts/query.hpp"

namespace screenlate_language {

// One candidate of the language script; field names follow its JSON.
struct LanguageCandidate {
  std::string o;  // substring of the scanned text
  std::string t;  // after the text preprocessors
  std::string d;  // deinflected text to look up
  int s = 0;      // preprocessor steps
  uint32_t c = 0;  // condition flags; 0 matches any entry
  std::vector<std::vector<std::string>> r;  // inflection rule chains
};

std::vector<LanguageCandidate> parse_candidates(const std::string& json);

std::unordered_map<std::string, uint32_t> parse_part_of_speech_flags(const std::string& json);

struct CandidateLookupOptions {
  int max_results = 16;
  std::optional<std::string> frequency_dictionary;
  bool frequency_descending = false;
  std::optional<std::string> primary_reading;
};

struct CandidateResult {
  std::string matched;
  std::string deinflected;
  // Rule chains from the matched text to the term, the shortest first; Yomitan lists every one.
  std::vector<std::vector<std::string>> chains;
  int preprocessor_steps = 0;
  TermResult term;
};

// Results for [candidates], best first. [tables] are the form-of tables of the loaded term dictionaries.
std::vector<CandidateResult> lookup_candidates(const DictionaryQuery& query, const std::vector<const FormOfTable*>& tables,
                                               const std::vector<LanguageCandidate>& candidates,
                                               const std::unordered_map<std::string, uint32_t>& part_of_speech_flags,
                                               const CandidateLookupOptions& options);

}  // namespace screenlate_language
