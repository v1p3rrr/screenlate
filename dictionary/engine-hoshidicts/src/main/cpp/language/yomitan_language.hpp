// Yomitan's language code (text processors and deinflection transforms) running in QuickJS.
#pragma once

#include <string>

struct JSRuntime;
struct JSContext;

namespace screenlate_language {

// One QuickJS runtime holding a bundle that defines `globalThis.yomitanLang`. Not thread-safe.
class YomitanLanguage {
 public:
  explicit YomitanLanguage(const std::string& script);
  ~YomitanLanguage();

  YomitanLanguage(const YomitanLanguage&) = delete;
  YomitanLanguage& operator=(const YomitanLanguage&) = delete;

  // JSON array of {o, t, d, s, c, r}: original substring, preprocessed text, deinflected text, preprocessor
  // steps, condition flags, inflection rule chains.
  std::string candidates_json(const std::string& text, const std::string& language, const std::string& resolution);

  // JSON object mapping part-of-speech names in dictionary `rules` to condition flags.
  std::string part_of_speech_flags_json(const std::string& language);

 private:
  std::string call(const char* function, const std::string* args, int count);

  JSRuntime* runtime_ = nullptr;
  JSContext* context_ = nullptr;
};

}  // namespace screenlate_language
