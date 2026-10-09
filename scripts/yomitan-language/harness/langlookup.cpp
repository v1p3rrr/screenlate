// Experiment harness: Yomitan candidates (QuickJS) -> hoshidicts exact queries -> form-of -> sorted results.
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "candidate_lookup.hpp"
#include "yomitan_language.hpp"

using namespace screenlate_language;

static double ms_since(std::chrono::steady_clock::time_point t) {
  return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t).count();
}

int main(int argc, char** argv) {
  if (argc < 6) {
    std::cerr << "usage: langlookup bundle.js language resolution dict... -- text...\n";
    return 1;
  }
  std::ifstream in(argv[1], std::ios::binary);
  std::stringstream buffer;
  buffer << in.rdbuf();
  auto t = std::chrono::steady_clock::now();
  YomitanLanguage js(buffer.str());
  std::string language = argv[2], resolution = argv[3];
  auto flags = parse_part_of_speech_flags(js.part_of_speech_flags_json(language));
  std::cout << "js load " << ms_since(t) << " ms\n";
  int i = 4;
  t = std::chrono::steady_clock::now();
  DictionaryQuery query;
  for (; i < argc && std::string(argv[i]) != "--"; i++) query.add_term_dict(argv[i]);
  std::cout << "dict load " << ms_since(t) << " ms\n";
  for (i++; i < argc; i++) {
    std::string text = argv[i];
    for (int round = 0; round < 2; round++) {
      auto t0 = std::chrono::steady_clock::now();
      auto candidates = parse_candidates(js.candidates_json(text, language, resolution));
      double t_js = ms_since(t0);
      auto t1 = std::chrono::steady_clock::now();
      auto results = lookup_candidates(query, candidates, flags, 8);
      double t_lookup = ms_since(t1);
      std::cout << "\n## " << text << " | candidates " << candidates.size() << " js " << t_js << " ms, lookup "
                << t_lookup << " ms\n";
      if (round == 1) continue;
      for (const auto& r : results) {
        std::cout << "  [" << r.matched << "] " << r.term.expression << " (" << r.term.reading << ") rules=" << r.term.rules
                  << " steps=" << r.preprocessor_steps << " trace=";
        for (const auto& g : r.trace) std::cout << g.name << " > ";
        std::cout << " glossaries=" << r.term.glossaries.size() << "\n      "
                  << r.term.glossaries.front().glossary.substr(0, 140) << "\n";
      }
    }
  }
  return 0;
}
