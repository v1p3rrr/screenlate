// Experiment: lookup over deinflection candidates produced outside hoshidicts (Yomitan's language code), with
// dictionary deinflection: glossary items `["lemma", ["rule", ...]]` lead to the lemma, as in Yomitan.
#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

#include "hoshidicts/lookup.hpp"
#include "hoshidicts/query.hpp"

namespace screenlate_language {

// Field names follow the JSON written by the language script.
struct LanguageCandidate {
  std::string o;  // substring of the scanned text
  std::string t;  // after text preprocessors
  std::string d;  // deinflected text to look up
  int s = 0;      // preprocessor steps
  uint32_t c = 0;  // condition flags; 0 matches any entry
  std::vector<std::vector<std::string>> r;  // inflection rule chains
};

std::vector<LanguageCandidate> parse_candidates(const std::string& json);

std::unordered_map<std::string, uint32_t> parse_part_of_speech_flags(const std::string& json);

std::vector<LookupResult> lookup_candidates(const DictionaryQuery& query, const std::vector<LanguageCandidate>& candidates,
                                            const std::unordered_map<std::string, uint32_t>& part_of_speech_flags,
                                            int max_results);

}  // namespace screenlate_language
