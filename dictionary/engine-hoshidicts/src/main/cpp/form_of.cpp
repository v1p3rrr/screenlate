#include "form_of.hpp"

#include <utf8.h>

#include <map>
#include <tuple>
#include <unordered_map>
#include <utility>

#include <glaze/glaze.hpp>

namespace screenlate_form_of {
namespace {

bool is_space(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }

size_t length_of(const std::string& text) { return utf8::unchecked::distance(text.begin(), text.end()); }

// Whether [a] matches the scanned text better than [b], in the order hoshidicts and Yomitan sort by before frequencies.
bool better(const LookupResult& a, const LookupResult& b) {
  const size_t length_a = length_of(a.matched);
  const size_t length_b = length_of(b.matched);
  if (length_a != length_b) return length_a > length_b;
  if (a.preprocessor_steps != b.preprocessor_steps) return a.preprocessor_steps < b.preprocessor_steps;
  return a.trace.size() < b.trace.size();
}

// Moves the form-of items of [term]'s glossaries to [items] and drops the glossaries left empty.
void strip_term(TermResult& term, std::vector<FormOfItem>& items) {
  for (auto& glossary : term.glossaries) glossary.glossary = strip_form_of(glossary.glossary, items);
  std::erase_if(term.glossaries, [](const GlossaryEntry& g) { return g.glossary.empty(); });
}

// What a result's form-of items carry over to the lemmas they lead to.
struct Source {
  std::string matched;
  std::vector<TransformGroup> trace;
  int preprocessor_steps = 0;
};

}  // namespace

std::string strip_form_of(const std::string& glossary, std::vector<FormOfItem>& items) {
  if (glossary.find('[', 1) == std::string::npos) return glossary;
  std::vector<glz::raw_json> parts;
  if (glz::read_json(parts, glossary)) return glossary;
  std::string rest = "[";
  bool removed = false;
  bool empty = true;
  for (const auto& part : parts) {
    const std::string& raw = part.str;
    size_t first = 0;
    while (first < raw.size() && is_space(raw[first])) first++;
    std::tuple<std::string, std::vector<std::string>> parsed;
    if (first < raw.size() && raw[first] == '[' && !glz::read_json(parsed, raw) && !std::get<0>(parsed).empty()) {
      removed = true;
      items.push_back({std::move(std::get<0>(parsed)), std::move(std::get<1>(parsed))});
      continue;
    }
    if (!empty) rest += ',';
    rest += raw;
    empty = false;
  }
  if (!removed) return glossary;
  return empty ? std::string() : rest + "]";
}

std::vector<LookupResult> follow_form_of(const DictionaryQuery& query, std::vector<LookupResult> results) {
  std::vector<std::vector<FormOfItem>> items(results.size());
  std::vector<Source> sources(results.size());
  bool any = false;
  for (size_t i = 0; i < results.size(); i++) {
    strip_term(results[i].term, items[i]);
    if (items[i].empty()) continue;
    any = true;
    sources[i] = {results[i].matched, results[i].trace, results[i].preprocessor_steps};
  }
  if (!any) return results;

  // Each kept result stays in its place, followed by the new terms its form-of items lead to.
  using Key = std::pair<std::string, std::string>;
  std::vector<std::vector<LookupResult>> slots(results.size());
  std::map<Key, std::pair<size_t, size_t>> placed;
  for (size_t i = 0; i < results.size(); i++) {
    if (results[i].term.glossaries.empty()) continue;
    placed.emplace(Key{results[i].term.expression, results[i].term.reading}, std::pair{i, size_t{0}});
    slots[i].push_back(std::move(results[i]));
  }

  std::unordered_map<std::string, std::vector<TermResult>> lemmas;
  for (size_t i = 0; i < results.size(); i++) {
    for (const auto& item : items[i]) {
      auto found = lemmas.find(item.lemma);
      if (found == lemmas.end()) {
        std::vector<TermResult> terms = query.query(item.lemma);
        // One step, as Yomitan: the lemma's own form-of items are dropped, not followed.
        std::vector<FormOfItem> further;
        for (auto& term : terms) strip_term(term, further);
        std::erase_if(terms, [](const TermResult& t) { return t.glossaries.empty(); });
        found = lemmas.emplace(item.lemma, std::move(terms)).first;
      }
      for (const auto& term : found->second) {
        LookupResult lemma{.matched = sources[i].matched,
                           .deinflected = item.lemma,
                           .trace = sources[i].trace,
                           .term = term,
                           .preprocessor_steps = sources[i].preprocessor_steps};
        for (const auto& tag : item.tags) lemma.trace.push_back(TransformGroup{tag, ""});
        Key key{term.expression, term.reading};
        auto existing = placed.find(key);
        if (existing == placed.end()) {
          placed.emplace(std::move(key), std::pair{i, slots[i].size()});
          slots[i].push_back(std::move(lemma));
        } else {
          LookupResult& current = slots[existing->second.first][existing->second.second];
          if (better(lemma, current)) current = std::move(lemma);
        }
      }
    }
  }

  std::vector<LookupResult> followed;
  for (auto& slot : slots) {
    for (auto& result : slot) followed.push_back(std::move(result));
  }
  return followed;
}

}  // namespace screenlate_form_of
