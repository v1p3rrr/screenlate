// JNI bridge between HoshidictsNative (Kotlin) and hoshidicts.
//
// Strings cross the boundary as UTF-8 byte arrays: JNI's "modified UTF-8" encodes characters outside the
// BMP (for example U+20B9F) as surrogate pairs, which hoshidicts would not match.
// Structured results are returned as UTF-8 JSON produced with glaze.
//
// Japanese is looked up by hoshidicts itself. Other languages take their deinflection candidates from Yomitan's
// language code running in QuickJS and follow form-of entries through the tables in language/.

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <exception>
#include <filesystem>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include <glaze/glaze.hpp>
#include <utf8.h>

#include "hoshidicts/deinflector.hpp"
#include "hoshidicts/importer.hpp"
#include "hoshidicts/lookup.hpp"
#include "hoshidicts/query.hpp"
#include "language/archive_split.hpp"
#include "language/candidate_lookup.hpp"
#include "language/form_of_table.hpp"
#include "language/yomitan_language.hpp"

// glaze reflection needs types with linkage, so this is a named namespace.
namespace screenlate_jni {

constexpr const char* kFormOfTable = "form_of.bin";
constexpr const char* kSplitArchive = "form-of-split.zip";

struct LanguageScript {
  std::unique_ptr<screenlate_language::YomitanLanguage> script;
  std::unordered_map<std::string, uint32_t> part_of_speech_flags;
};

struct Session {
  DictionaryQuery query;
  Deinflector deinflector;
  // Form-of tables of the loaded term dictionaries that have one.
  std::vector<std::unique_ptr<screenlate_language::FormOfTable>> form_of_tables;
  // Yomitan's language code by ISO 639-1 code, for the languages looked up so far.
  std::unordered_map<std::string, LanguageScript> languages;
};

struct TransformDto {
  std::string name;
  std::string description;
};

// The glossary stays JSON text inside a string: parsing it into a tree in Kotlin and writing it back for the
// page costs far more than letting the page's JSON.parse handle it.
struct GlossaryDto {
  std::string dictionary;
  std::string content;
  std::string definitionTags;
  std::string termTags;
};

struct FrequencyValueDto {
  int value = 0;
  std::string displayValue;
};

struct FrequencyDto {
  std::string dictionary;
  std::vector<FrequencyValueDto> values;
};

struct PitchValueDto {
  int position = 0;
  std::string pattern;
  std::vector<int> nasal;
  std::vector<int> devoice;
};

struct PitchDto {
  std::string dictionary;
  std::vector<PitchValueDto> pitches;
  std::vector<std::string> transcriptions;
};

struct TermDto {
  std::string expression;
  std::string reading;
  std::string rules;
  int score = 0;
  std::vector<GlossaryDto> glossaries;
  std::vector<FrequencyDto> frequencies;
  std::vector<PitchDto> pitches;
};

struct LookupDto {
  std::string matched;
  std::string deinflected;
  std::vector<TransformDto> trace;
  // Further rule chains to the same term (languages other than Japanese).
  std::vector<std::vector<TransformDto>> otherTraces;
  TermDto term;
  int preprocessorSteps = 0;
};

struct ImportDto {
  bool success = false;
  std::string title;
  std::string error;
  Summary summary;
};

struct StyleDto {
  std::string dictionary;
  std::string css;
};

struct KanjiEntryDto {
  std::string dictionary;
  std::string onyomi;
  std::string kunyomi;
  std::string tags;
  std::vector<std::string> definitions;
  std::unordered_map<std::string, std::string> stats;
};

struct KanjiDto {
  std::string character;
  std::vector<KanjiEntryDto> entries;
};

std::string to_string(JNIEnv* env, jbyteArray bytes) {
  if (bytes == nullptr) {
    return {};
  }
  const jsize length = env->GetArrayLength(bytes);
  std::string result(static_cast<size_t>(length), '\0');
  env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(result.data()));
  return result;
}

std::optional<std::string> to_optional_string(JNIEnv* env, jbyteArray bytes) {
  if (bytes == nullptr) {
    return std::nullopt;
  }
  return to_string(env, bytes);
}

std::vector<std::string> to_strings(JNIEnv* env, jobjectArray array) {
  std::vector<std::string> result;
  if (array == nullptr) {
    return result;
  }
  const jsize count = env->GetArrayLength(array);
  result.reserve(static_cast<size_t>(count));
  for (jsize i = 0; i < count; i++) {
    auto element = static_cast<jbyteArray>(env->GetObjectArrayElement(array, i));
    result.push_back(to_string(env, element));
    env->DeleteLocalRef(element);
  }
  return result;
}

jbyteArray to_bytes(JNIEnv* env, const char* data, size_t size) {
  jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
  if (result != nullptr && size > 0) {
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
  }
  return result;
}

jbyteArray to_bytes(JNIEnv* env, const std::string& value) { return to_bytes(env, value.data(), value.size()); }

void throw_runtime(JNIEnv* env, const std::string& message) {
  if (env->ExceptionCheck()) {
    return;
  }
  jclass type = env->FindClass("java/lang/RuntimeException");
  if (type != nullptr) {
    env->ThrowNew(type, message.c_str());
  }
}

template <class T>
jbyteArray write_json(JNIEnv* env, const T& value) {
  std::string json;
  if (auto error = glz::write_json(value, json)) {
    throw_runtime(env, "JSON serialization failed: " + glz::format_error(error, json));
    return nullptr;
  }
  return to_bytes(env, json);
}

Session* session(jlong handle) { return reinterpret_cast<Session*>(handle); }

void fill_term(TermDto& dto, TermResult& term);

LookupDto to_dto(LookupResult&& result) {
  LookupDto dto;
  dto.matched = std::move(result.matched);
  dto.deinflected = std::move(result.deinflected);
  dto.preprocessorSteps = result.preprocessor_steps;
  for (auto& group : result.trace) {
    dto.trace.push_back({std::move(group.name), std::move(group.description)});
  }
  fill_term(dto.term, result.term);
  return dto;
}

// Rule names only: their descriptions are resources on the Kotlin side.
std::vector<TransformDto> to_transforms(std::vector<std::string>&& chain) {
  std::vector<TransformDto> transforms;
  transforms.reserve(chain.size());
  for (auto& name : chain) transforms.push_back({std::move(name), {}});
  return transforms;
}

LookupDto to_dto(screenlate_language::CandidateResult&& result) {
  LookupDto dto;
  dto.matched = std::move(result.matched);
  dto.deinflected = std::move(result.deinflected);
  dto.preprocessorSteps = result.preprocessor_steps;
  for (size_t i = 0; i < result.chains.size(); i++) {
    if (i == 0) {
      dto.trace = to_transforms(std::move(result.chains[i]));
    } else {
      dto.otherTraces.push_back(to_transforms(std::move(result.chains[i])));
    }
  }
  fill_term(dto.term, result.term);
  return dto;
}

void fill_term(TermDto& dto, TermResult& term) {
  dto.expression = std::move(term.expression);
  dto.reading = std::move(term.reading);
  dto.rules = std::move(term.rules);
  dto.score = term.score;
  for (auto& glossary : term.glossaries) {
    GlossaryDto item;
    item.dictionary = std::move(glossary.dict_name);
    item.content = glossary.glossary.empty() ? std::string("[]") : std::move(glossary.glossary);
    item.definitionTags = std::move(glossary.definition_tags);
    item.termTags = std::move(glossary.term_tags);
    dto.glossaries.push_back(std::move(item));
  }
  for (auto& frequency : term.frequencies) {
    FrequencyDto item;
    item.dictionary = std::move(frequency.dict_name);
    for (auto& value : frequency.frequencies) {
      item.values.push_back({value.value, std::move(value.display_value)});
    }
    dto.frequencies.push_back(std::move(item));
  }
  for (auto& pitch : term.pitches) {
    PitchDto item;
    item.dictionary = std::move(pitch.dict_name);
    item.transcriptions = std::move(pitch.transcriptions);
    for (auto& value : pitch.pitches) {
      item.pitches.push_back({value.position, std::move(value.pattern), std::move(value.nasal), std::move(value.devoice)});
    }
    dto.pitches.push_back(std::move(item));
  }
}

// The first [count] characters of [text].
std::string prefix(const std::string& text, size_t count) {
  auto end = text.begin();
  const auto length = static_cast<size_t>(utf8::unchecked::distance(text.begin(), text.end()));
  utf8::unchecked::advance(end, std::min(count, length));
  return std::string(text.begin(), end);
}

}  // namespace screenlate_jni

using namespace screenlate_jni;

extern "C" {

JNIEXPORT jlong JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_create(JNIEnv* env, jobject) {
  try {
    return reinterpret_cast<jlong>(new Session());
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return 0;
  }
}

JNIEXPORT void JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_destroy(JNIEnv*, jobject,
                                                                                                    jlong handle) {
  delete session(handle);
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_importDictionary(
    JNIEnv* env, jobject, jbyteArray zip_path, jbyteArray output_dir, jboolean low_ram) {
  try {
    const std::string archive = to_string(env, zip_path);
    const std::filesystem::path output = to_string(env, output_dir);
    // Form-of rows go into our own table; hoshidicts imports the archive without them.
    const std::string split_path = (output / kSplitArchive).string();
    screenlate_language::ArchiveSplit split = screenlate_language::split_form_of(archive, split_path);
    ImportResult result = dictionary_importer::import(split.written ? split_path : archive, output.string(),
                                                      low_ram == JNI_TRUE);
    std::error_code error;
    if (split.written) std::filesystem::remove(split_path, error);
    if (result.success && split.written) {
      split.table.write(output / result.title / kFormOfTable);
      // A dictionary of forms only still counts as a term dictionary, so it is loaded.
      result.summary.counts.terms.total += split.form_rows;
      __android_log_print(ANDROID_LOG_INFO, "HoshidictsNative", "Form-of table: %zu forms, %zu entries, %zu mixed rows",
                          split.table.form_count(), split.table.entry_count(), split.mixed_rows);
    }
    ImportDto dto{result.success, std::move(result.title), std::move(result.error), std::move(result.summary)};
    return write_json(env, dto);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

JNIEXPORT void JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_load(
    JNIEnv* env, jobject, jlong handle, jobjectArray term_paths, jobjectArray frequency_paths,
    jobjectArray pitch_paths, jobjectArray kanji_paths) {
  try {
    DictionaryQuery query;
    for (const auto& path : to_strings(env, term_paths)) query.add_term_dict(path);
    for (const auto& path : to_strings(env, frequency_paths)) query.add_freq_dict(path);
    for (const auto& path : to_strings(env, pitch_paths)) query.add_pitch_dict(path);
    for (const auto& path : to_strings(env, kanji_paths)) query.add_kanji_dict(path);
    std::vector<std::unique_ptr<screenlate_language::FormOfTable>> tables;
    for (const auto& path : to_strings(env, term_paths)) {
      try {
        if (auto table = screenlate_language::FormOfTable::open(std::filesystem::path(path) / kFormOfTable)) {
          tables.push_back(std::move(table));
        }
      } catch (const std::exception& e) {
        // The registry reports such a dictionary as broken (HoshidictsEngine.isComplete); the others still load.
        __android_log_print(ANDROID_LOG_WARN, "HoshidictsNative", "Skipping a form-of table: %s", e.what());
      }
    }
    Session* s = session(handle);
    s->query = std::move(query);
    s->form_of_tables = std::move(tables);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
  }
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_lookup(
    JNIEnv* env, jobject, jlong handle, jbyteArray text, jint max_results, jint scan_length,
    jbyteArray frequency_dictionary, jint frequency_order, jbyteArray primary_reading) {
  try {
    Session* s = session(handle);
    LookupOptions options;
    options.frequency_dictionary = to_optional_string(env, frequency_dictionary);
    options.frequency_order = static_cast<LookupFrequencyOrder>(frequency_order);
    options.primary_reading = to_optional_string(env, primary_reading);

    Lookup lookup(s->query, s->deinflector);
    std::vector<LookupResult> results =
        lookup.lookup(to_string(env, text), max_results, static_cast<size_t>(scan_length), options);
    std::vector<LookupDto> dtos;
    dtos.reserve(results.size());
    for (auto& result : results) {
      dtos.push_back(to_dto(std::move(result)));
    }
    return write_json(env, dtos);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jboolean JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_hasLanguage(
    JNIEnv* env, jobject, jlong handle, jbyteArray language) {
  return session(handle)->languages.contains(to_string(env, language)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_loadLanguage(
    JNIEnv* env, jobject, jlong handle, jbyteArray language, jbyteArray script) {
  try {
    const std::string code = to_string(env, language);
    LanguageScript loaded;
    loaded.script = std::make_unique<screenlate_language::YomitanLanguage>(to_string(env, script));
    loaded.part_of_speech_flags =
        screenlate_language::parse_part_of_speech_flags(loaded.script->part_of_speech_flags_json(code));
    session(handle)->languages.insert_or_assign(code, std::move(loaded));
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
  }
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_lookupLanguage(
    JNIEnv* env, jobject, jlong handle, jbyteArray language, jbyteArray text, jbyteArray resolution, jint max_results,
    jint scan_length, jbyteArray frequency_dictionary, jboolean frequency_descending, jbyteArray primary_reading) {
  try {
    Session* s = session(handle);
    const std::string code = to_string(env, language);
    auto loaded = s->languages.find(code);
    if (loaded == s->languages.end()) {
      throw_runtime(env, "language not loaded: " + code);
      return nullptr;
    }
    const std::string source = prefix(to_string(env, text), static_cast<size_t>(std::max(scan_length, 0)));
    auto candidates = screenlate_language::parse_candidates(
        loaded->second.script->candidates_json(source, code, to_string(env, resolution)));
    screenlate_language::CandidateLookupOptions options;
    options.max_results = max_results;
    options.frequency_dictionary = to_optional_string(env, frequency_dictionary);
    options.frequency_descending = frequency_descending == JNI_TRUE;
    options.primary_reading = to_optional_string(env, primary_reading);
    std::vector<const screenlate_language::FormOfTable*> tables;
    for (const auto& table : s->form_of_tables) tables.push_back(table.get());
    auto results = screenlate_language::lookup_candidates(s->query, tables, candidates,
                                                          loaded->second.part_of_speech_flags, options);
    std::vector<LookupDto> dtos;
    dtos.reserve(results.size());
    for (auto& result : results) {
      dtos.push_back(to_dto(std::move(result)));
    }
    return write_json(env, dtos);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_styles(JNIEnv* env,
                                                                                                         jobject,
                                                                                                         jlong handle) {
  try {
    std::vector<StyleDto> dtos;
    for (auto& style : session(handle)->query.get_styles()) {
      dtos.push_back({std::move(style.dict_name), std::move(style.styles)});
    }
    return write_json(env, dtos);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_media(
    JNIEnv* env, jobject, jlong handle, jbyteArray dictionary, jbyteArray path) {
  try {
    MediaFileView view = session(handle)->query.get_media_file_view(to_string(env, dictionary), to_string(env, path));
    if (view.data == nullptr) {
      return nullptr;
    }
    return to_bytes(env, view.data, view.size);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL Java_com_vpr_screenlate_dictionary_engine_hoshidicts_HoshidictsNative_kanji(
    JNIEnv* env, jobject, jlong handle, jbyteArray character) {
  try {
    KanjiResult result = session(handle)->query.query_kanji(to_string(env, character));
    KanjiDto dto;
    dto.character = std::move(result.character);
    for (auto& entry : result.entries) {
      dto.entries.push_back({std::move(entry.dict_name), std::move(entry.onyomi), std::move(entry.kunyomi),
                             std::move(entry.tags), std::move(entry.definitions), std::move(entry.stats)});
    }
    return write_json(env, dto);
  } catch (const std::exception& e) {
    throw_runtime(env, e.what());
    return nullptr;
  }
}

}  // extern "C"
