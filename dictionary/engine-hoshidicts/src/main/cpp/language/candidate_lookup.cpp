#include "candidate_lookup.hpp"

#include <utf8.h>

#include <algorithm>
#include <climits>
#include <map>
#include <optional>
#include <sstream>
#include <tuple>

#include <glaze/glaze.hpp>

namespace screenlate_language {
namespace {

struct FormOf {
  std::string lemma;
  std::vector<std::string> rules;
};

struct Match {
  LanguageCandidate candidate;
  TermResult term;
};

uint32_t flags_of(const std::string& rules, const std::unordered_map<std::string, uint32_t>& part_of_speech_flags) {
  uint32_t flags = 0;
  std::istringstream stream(rules);
  std::string rule;
  while (stream >> rule) {
    auto it = part_of_speech_flags.find(rule);
    if (it != part_of_speech_flags.end()) flags |= it->second;
  }
  return flags;
}

// Removes array items from a glossary, as Yomitan drops them from definitions, and collects the form-of ones.
// Returns the remaining JSON array, or an empty string when nothing remains.
std::string strip_form_of(const std::string& glossary, std::vector<FormOf>& form_of) {
  std::vector<glz::raw_json> items;
  if (glz::read_json(items, glossary)) return glossary;
  std::string rest = "[";
  bool removed = false;
  bool empty = true;
  for (const auto& item : items) {
    const std::string& raw = item.str;
    const size_t first = raw.find_first_not_of(" \t\r\n");
    if (first != std::string::npos && raw[first] == '[') {
      removed = true;
      std::tuple<std::string, std::vector<std::string>> parsed;
      if (!glz::read_json(parsed, raw) && !std::get<0>(parsed).empty()) {
        form_of.push_back({std::move(std::get<0>(parsed)), std::move(std::get<1>(parsed))});
      }
      continue;
    }
    if (!empty) rest += ',';
    rest += raw;
    empty = false;
  }
  if (!removed) return glossary;
  return empty ? std::string() : rest + "]";
}

void strip_term(TermResult& term, std::vector<FormOf>& form_of) {
  for (auto& glossary : term.glossaries) {
    glossary.glossary = strip_form_of(glossary.glossary, form_of);
  }
  std::erase_if(term.glossaries, [](const GlossaryEntry& g) { return g.glossary.empty(); });
}

std::optional<int> frequency_in(const TermResult& term, std::string_view dictionary) {
  std::optional<int> best;
  for (const auto& entry : term.frequencies) {
    if (entry.dict_name != dictionary) continue;
    for (const auto& f : entry.frequencies) {
      if (f.value < 0) continue;
      best = best.has_value() ? std::min(*best, f.value) : f.value;
    }
  }
  return best;
}

}  // namespace

std::vector<LanguageCandidate> parse_candidates(const std::string& json) {
  std::vector<LanguageCandidate> result;
  if (auto error = glz::read<glz::opts{.error_on_unknown_keys = false}>(result, json)) {
    throw std::runtime_error("candidates: " + glz::format_error(error, json));
  }
  return result;
}

std::unordered_map<std::string, uint32_t> parse_part_of_speech_flags(const std::string& json) {
  std::unordered_map<std::string, uint32_t> result;
  if (auto error = glz::read_json(result, json)) {
    throw std::runtime_error("part of speech flags: " + glz::format_error(error, json));
  }
  return result;
}

std::vector<LookupResult> lookup_candidates(const DictionaryQuery& query, const std::vector<LanguageCandidate>& candidates,
                                            const std::unordered_map<std::string, uint32_t>& part_of_speech_flags,
                                            int max_results) {
  std::unordered_map<std::string, std::vector<TermResult>> cache;
  auto terms_for = [&](const std::string& text) -> const std::vector<TermResult>& {
    auto it = cache.find(text);
    if (it == cache.end()) it = cache.emplace(text, query.query(text)).first;
    return it->second;
  };

  std::vector<Match> matches;
  std::vector<LanguageCandidate> lemma_candidates;
  for (const auto& candidate : candidates) {
    for (const auto& term : terms_for(candidate.d)) {
      if (candidate.c != 0 && (candidate.c & flags_of(term.rules, part_of_speech_flags)) == 0) continue;
      TermResult copy = term;
      std::vector<FormOf> form_of;
      strip_term(copy, form_of);
      for (auto& item : form_of) {
        LanguageCandidate lemma{candidate.o, candidate.t, item.lemma, candidate.s, 0, {}};
        for (const auto& chain : candidate.r) {
          auto extended = chain;
          extended.insert(extended.end(), item.rules.begin(), item.rules.end());
          lemma.r.push_back(std::move(extended));
        }
        lemma_candidates.push_back(std::move(lemma));
      }
      if (!copy.glossaries.empty()) matches.push_back({candidate, std::move(copy)});
    }
  }
  for (const auto& candidate : lemma_candidates) {
    for (const auto& term : terms_for(candidate.d)) {
      TermResult copy = term;
      std::vector<FormOf> ignored;
      strip_term(copy, ignored);
      if (!copy.glossaries.empty()) matches.push_back({candidate, std::move(copy)});
    }
  }

  // One result per headword, from its longest matched text.
  std::map<std::pair<std::string, std::string>, Match> by_term;
  for (auto& match : matches) {
    auto key = std::make_pair(match.term.expression, match.term.reading);
    auto it = by_term.find(key);
    if (it == by_term.end()) {
      by_term.emplace(std::move(key), std::move(match));
    } else if (utf8::distance(match.candidate.o.begin(), match.candidate.o.end()) >
               utf8::distance(it->second.candidate.o.begin(), it->second.candidate.o.end())) {
      it->second = std::move(match);
    }
  }

  std::vector<LookupResult> results;
  for (auto& [key, match] : by_term) {
    LookupResult result{.matched = match.candidate.o,
                        .deinflected = match.candidate.d,
                        .trace = {},
                        .term = std::move(match.term),
                        .preprocessor_steps = match.candidate.s};
    if (!match.candidate.r.empty()) {
      for (const auto& rule : match.candidate.r.front()) result.trace.push_back({rule, ""});
    }
    results.push_back(std::move(result));
  }

  const auto frequency_dictionaries = query.get_freq_dict_order();
  std::ranges::stable_sort(results, [&](const LookupResult& a, const LookupResult& b) {
    const auto length_a = utf8::distance(a.matched.begin(), a.matched.end());
    const auto length_b = utf8::distance(b.matched.begin(), b.matched.end());
    if (length_a != length_b) return length_a > length_b;
    if (a.preprocessor_steps != b.preprocessor_steps) return a.preprocessor_steps < b.preprocessor_steps;
    if (a.trace.size() != b.trace.size()) return a.trace.size() < b.trace.size();
    const bool exact_a = a.term.expression == a.deinflected;
    const bool exact_b = b.term.expression == b.deinflected;
    if (exact_a != exact_b) return exact_a;
    for (const auto& dictionary : frequency_dictionaries) {
      const int frequency_a = frequency_in(a.term, dictionary).value_or(INT_MAX);
      const int frequency_b = frequency_in(b.term, dictionary).value_or(INT_MAX);
      if (frequency_a != frequency_b) return frequency_a < frequency_b;
    }
    return a.term.score > b.term.score;
  });
  if (results.size() > static_cast<size_t>(max_results)) results.resize(static_cast<size_t>(max_results));
  return results;
}

}  // namespace screenlate_language
