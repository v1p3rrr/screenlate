// JNI bridge between HoshidictsNative (Kotlin) and hoshidicts.
//
// Strings cross the boundary as UTF-8 byte arrays: JNI's "modified UTF-8" encodes characters outside the
// BMP (for example U+20B9F) as surrogate pairs, which hoshidicts would not match.
// Structured results are returned as UTF-8 JSON produced with glaze.

#include <jni.h>

#include <exception>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include <glaze/glaze.hpp>

#include "hoshidicts/deinflector.hpp"
#include "hoshidicts/importer.hpp"
#include "hoshidicts/lookup.hpp"
#include "hoshidicts/query.hpp"

// glaze reflection needs types with linkage, so this is a named namespace.
namespace screenlate_jni {

struct Session {
  DictionaryQuery query;
  Deinflector deinflector;
};

struct TransformDto {
  std::string name;
  std::string description;
};

struct GlossaryDto {
  std::string dictionary;
  glz::raw_json content;
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

LookupDto to_dto(LookupResult&& result) {
  LookupDto dto;
  dto.matched = std::move(result.matched);
  dto.deinflected = std::move(result.deinflected);
  dto.preprocessorSteps = result.preprocessor_steps;
  for (auto& group : result.trace) {
    dto.trace.push_back({std::move(group.name), std::move(group.description)});
  }

  TermResult& term = result.term;
  dto.term.expression = std::move(term.expression);
  dto.term.reading = std::move(term.reading);
  dto.term.rules = std::move(term.rules);
  dto.term.score = term.score;
  for (auto& glossary : term.glossaries) {
    GlossaryDto item;
    item.dictionary = std::move(glossary.dict_name);
    item.content = glossary.glossary.empty() ? std::string("[]") : std::move(glossary.glossary);
    item.definitionTags = std::move(glossary.definition_tags);
    item.termTags = std::move(glossary.term_tags);
    dto.term.glossaries.push_back(std::move(item));
  }
  for (auto& frequency : term.frequencies) {
    FrequencyDto item;
    item.dictionary = std::move(frequency.dict_name);
    for (auto& value : frequency.frequencies) {
      item.values.push_back({value.value, std::move(value.display_value)});
    }
    dto.term.frequencies.push_back(std::move(item));
  }
  for (auto& pitch : term.pitches) {
    PitchDto item;
    item.dictionary = std::move(pitch.dict_name);
    item.transcriptions = std::move(pitch.transcriptions);
    for (auto& value : pitch.pitches) {
      item.pitches.push_back({value.position, std::move(value.pattern), std::move(value.nasal), std::move(value.devoice)});
    }
    dto.term.pitches.push_back(std::move(item));
  }
  return dto;
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
    ImportResult result =
        dictionary_importer::import(to_string(env, zip_path), to_string(env, output_dir), low_ram == JNI_TRUE);
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
    session(handle)->query = std::move(query);
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
