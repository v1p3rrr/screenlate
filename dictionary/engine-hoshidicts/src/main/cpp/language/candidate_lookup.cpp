#include "candidate_lookup.hpp"

#include <utf8.h>

#include <algorithm>
#include <climits>
#include <map>
#include <sstream>
#include <stdexcept>

#include <glaze/glaze.hpp>

#include "../form_of.hpp"

namespace screenlate_language {
// glaze reflection needs types with linkage.
namespace candidate_json {

// As the script writes it: JavaScript's bit operations give signed 32-bit numbers.
struct RawCandidate {
  std::string o;
  std::string t;
  std::string d;
  int s = 0;
  int64_t c = 0;
  std::vector<std::vector<std::string>> r;
  bool u = false;
};

}  // namespace candidate_json

namespace {

using candidate_json::RawCandidate;

// A text to look up and how the scanned text led to it.
struct Pending {
  std::string matched;
  int steps = 0;
  uint32_t conditions = 0;
  std::vector<std::vector<std::string>> chains;
  std::string text;
  bool untyped = false;
  // The text whose form-of entry led to [text]; empty for a candidate.
  std::string from;
  // The scanned text after the text preprocessors, where the chains start.
  std::string origin;
};

struct Match {
  std::string matched;
  std::string deinflected;
  int steps = 0;
  std::vector<std::vector<std::string>> chains;
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

bool is_space(char c) { return c == ' ' || c == 9 || c == 10 || c == 13; }

// Whether an entry with part of speech [rules] fits the conditions of [p]; an entry without one fits an untyped
// candidate too (see `isUntypedPhrasalVerb` and `untypedRules` in the language script).
bool fits(const Pending& p, const std::string& rules,
          const std::unordered_map<std::string, uint32_t>& part_of_speech_flags) {
  if (p.conditions == 0 || (p.conditions & flags_of(rules, part_of_speech_flags)) != 0) return true;
  return p.untyped && std::all_of(rules.begin(), rules.end(), is_space);
}

// Removes form-of items from a glossary (`screenlate_form_of::strip_form_of`) and collects them with the row's rules.
// Dictionaries imported before the form-of table, and Japanese ones, still hold such items.
std::string strip_form_of(const std::string& glossary, const std::string& rules, std::vector<FormOf>& form_of) {
  std::vector<screenlate_form_of::FormOfItem> items;
  std::string rest = screenlate_form_of::strip_form_of(glossary, items);
  for (auto& item : items) form_of.push_back({std::move(item.lemma), std::move(item.tags), rules});
  return rest;
}

std::optional<int> frequency_in(const TermResult& term, const std::string& dictionary, bool descending) {
  std::optional<int> best;
  for (const auto& entry : term.frequencies) {
    if (entry.dict_name != dictionary) continue;
    for (const auto& f : entry.frequencies) {
      if (f.value < 0) continue;
      if (!best.has_value()) {
        best = f.value;
      } else {
        best = descending ? std::max(*best, f.value) : std::min(*best, f.value);
      }
    }
  }
  return best;
}

size_t length_of(const std::string& text) { return utf8::unchecked::distance(text.begin(), text.end()); }

size_t shortest(const std::vector<std::vector<std::string>>& chains) {
  size_t best = SIZE_MAX;
  for (const auto& chain : chains) best = std::min(best, chain.size());
  return best == SIZE_MAX ? 0 : best;
}

// Whether [a] is a better match of its headword than [b]: Yomitan's order before frequencies.
bool better(const Match& a, const Match& b) {
  const size_t length_a = length_of(a.matched);
  const size_t length_b = length_of(b.matched);
  if (length_a != length_b) return length_a > length_b;
  if (a.steps != b.steps) return a.steps < b.steps;
  const size_t chain_a = shortest(a.chains);
  const size_t chain_b = shortest(b.chains);
  if (chain_a != chain_b) return chain_a < chain_b;
  const bool exact_a = a.term.expression == a.deinflected;
  const bool exact_b = b.term.expression == b.deinflected;
  return exact_a && !exact_b;
}

}  // namespace

std::vector<LanguageCandidate> parse_candidates(const std::string& json) {
  std::vector<RawCandidate> raw;
  if (auto error = glz::read<glz::opts{.error_on_unknown_keys = false}>(raw, json)) {
    throw std::runtime_error("candidates: " + glz::format_error(error, json));
  }
  std::vector<LanguageCandidate> result;
  result.reserve(raw.size());
  for (auto& c : raw) {
    result.push_back(
        {std::move(c.o), std::move(c.t), std::move(c.d), c.s, static_cast<uint32_t>(c.c), std::move(c.r), c.u});
  }
  return result;
}

std::unordered_map<std::string, uint32_t> parse_part_of_speech_flags(const std::string& json) {
  std::unordered_map<std::string, int64_t> raw;
  if (auto error = glz::read_json(raw, json)) {
    throw std::runtime_error("part of speech flags: " + glz::format_error(error, json));
  }
  std::unordered_map<std::string, uint32_t> result;
  for (const auto& [name, flags] : raw) result.emplace(name, static_cast<uint32_t>(flags));
  return result;
}

std::vector<CandidateResult> lookup_candidates(const DictionaryQuery& query, const std::vector<const FormOfTable*>& tables,
                                               const std::vector<LanguageCandidate>& candidates,
                                               const std::unordered_map<std::string, uint32_t>& part_of_speech_flags,
                                               const CandidateLookupOptions& options) {
  std::unordered_map<std::string, std::vector<TermResult>> cache;
  auto terms_for = [&](const std::string& text) -> const std::vector<TermResult>& {
    auto it = cache.find(text);
    if (it == cache.end()) it = cache.emplace(text, query.query(text)).first;
    return it->second;
  };

  std::vector<Match> matches;
  // Adds the terms of [p] that have definitions; the lemmas its form-of entries lead to go to [next]. Returns whether
  // [p]'s text has definitions of its own.
  auto visit = [&](const Pending& p, std::vector<Pending>& next) {
    bool has_definitions = false;
    std::vector<FormOf> form_of;
    for (const auto& term : terms_for(p.text)) {
      if (!fits(p, term.rules, part_of_speech_flags)) continue;
      TermResult copy = term;
      for (auto& glossary : copy.glossaries) glossary.glossary = strip_form_of(glossary.glossary, copy.rules, form_of);
      std::erase_if(copy.glossaries, [](const GlossaryEntry& g) { return g.glossary.empty(); });
      if (copy.glossaries.empty()) continue;
      has_definitions = true;
      matches.push_back({p.matched, p.text, p.steps, p.chains, std::move(copy)});
    }
    for (const FormOfTable* table : tables) {
      for (auto& item : table->find(p.text)) {
        if (!fits(p, item.rules, part_of_speech_flags)) continue;
        form_of.push_back(std::move(item));
      }
    }
    for (const auto& item : form_of) {
      // A lemma that is the text itself, reached through another text, adds nothing: Yomitan's Ukrainian rules take
      // книга for the plural of книго, which a dictionary lists as the vocative of книга. A form that is its own lemma
      // (kuća, genitive plural of kuća) is a chain of one step and stays.
      if (item.lemma == p.origin && p.text != p.origin) continue;
      Pending lemma{p.matched, p.steps, 0, {}, item.lemma, false, p.text, p.origin};
      for (const auto& chain : p.chains) {
        auto extended = chain;
        extended.insert(extended.end(), item.tags.begin(), item.tags.end());
        lemma.chains.push_back(std::move(extended));
      }
      next.push_back(std::move(lemma));
    }
    return has_definitions;
  };

  std::vector<Pending> lemmas;
  for (const auto& candidate : candidates) {
    Pending p{candidate.o, candidate.s, candidate.c, candidate.r, candidate.d, candidate.u, {}, candidate.t};
    if (p.chains.empty()) p.chains.emplace_back();
    visit(p, lemmas);
  }
  // One form-of step, as Yomitan; a second one only from a lemma that is itself only a form (шокирующее →
  // шокирующий → шокировать), and not back to the text it came from (Jansen → Janssen → Jansen).
  std::vector<Pending> second;
  for (const auto& lemma : lemmas) {
    std::vector<Pending> further;
    if (visit(lemma, further)) continue;
    for (auto& next : further) {
      if (next.text != lemma.from) second.push_back(std::move(next));
    }
  }
  std::vector<Pending> ignored;
  for (const auto& lemma : second) visit(lemma, ignored);

  // Every match of a headword, keyed by expression and reading.
  std::map<std::pair<std::string, std::string>, std::vector<Match*>> by_term;
  for (auto& match : matches) by_term[{match.term.expression, match.term.reading}].push_back(&match);

  std::vector<CandidateResult> results;
  for (auto& [key, group] : by_term) {
    Match* best = group.front();
    for (Match* match : group) {
      if (better(*match, *best)) best = match;
    }
    CandidateResult result{best->matched, best->deinflected, {}, best->steps, std::move(best->term)};
    for (const Match* match : group) {
      if (match->matched != result.matched || match->steps != result.preprocessor_steps) continue;
      for (const auto& chain : match->chains) {
        if (std::find(result.chains.begin(), result.chains.end(), chain) == result.chains.end()) {
          result.chains.push_back(chain);
        }
      }
    }
    std::stable_sort(result.chains.begin(), result.chains.end(),
                     [](const auto& a, const auto& b) { return a.size() < b.size(); });
    results.push_back(std::move(result));
  }

  std::stable_sort(results.begin(), results.end(), [&](const CandidateResult& a, const CandidateResult& b) {
    if (options.primary_reading.has_value()) {
      const bool primary_a = a.term.reading == *options.primary_reading;
      const bool primary_b = b.term.reading == *options.primary_reading;
      if (primary_a != primary_b) return primary_a;
    }
    const size_t length_a = length_of(a.matched);
    const size_t length_b = length_of(b.matched);
    if (length_a != length_b) return length_a > length_b;
    if (a.preprocessor_steps != b.preprocessor_steps) return a.preprocessor_steps < b.preprocessor_steps;
    const size_t chain_a = shortest(a.chains);
    const size_t chain_b = shortest(b.chains);
    if (chain_a != chain_b) return chain_a < chain_b;
    const bool exact_a = a.term.expression == a.deinflected;
    const bool exact_b = b.term.expression == b.deinflected;
    if (exact_a != exact_b) return exact_a;
    if (options.frequency_dictionary.has_value()) {
      const auto frequency_a = frequency_in(a.term, *options.frequency_dictionary, options.frequency_descending);
      const auto frequency_b = frequency_in(b.term, *options.frequency_dictionary, options.frequency_descending);
      if (frequency_a.has_value() != frequency_b.has_value()) return frequency_a.has_value();
      if (frequency_a.has_value() && *frequency_a != *frequency_b) {
        return options.frequency_descending ? *frequency_a > *frequency_b : *frequency_a < *frequency_b;
      }
    }
    return a.term.score > b.term.score;
  });
  if (options.max_results >= 0 && results.size() > static_cast<size_t>(options.max_results)) {
    results.resize(static_cast<size_t>(options.max_results));
  }
  return results;
}

}  // namespace screenlate_language
