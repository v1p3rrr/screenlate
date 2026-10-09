#include "yomitan_language.hpp"

#include <stdexcept>

#include "quickjs.h"

namespace screenlate_language {
namespace {

std::string take_exception(JSContext* context) {
  JSValue exception = JS_GetException(context);
  const char* text = JS_ToCString(context, exception);
  std::string message = text != nullptr ? text : "unknown JS exception";
  JS_FreeCString(context, text);
  JS_FreeValue(context, exception);
  return message;
}

}  // namespace

YomitanLanguage::YomitanLanguage(const std::string& script) {
  runtime_ = JS_NewRuntime();
  if (runtime_ == nullptr) throw std::runtime_error("JS_NewRuntime failed");
  JS_SetMaxStackSize(runtime_, 1024 * 1024);
  context_ = JS_NewContext(runtime_);
  if (context_ == nullptr) {
    JS_FreeRuntime(runtime_);
    throw std::runtime_error("JS_NewContext failed");
  }
  JSValue result = JS_Eval(context_, script.c_str(), script.size(), "yomitan-language.js", JS_EVAL_TYPE_GLOBAL);
  if (JS_IsException(result)) {
    std::string message = take_exception(context_);
    JS_FreeContext(context_);
    JS_FreeRuntime(runtime_);
    throw std::runtime_error("language script: " + message);
  }
  JS_FreeValue(context_, result);
}

YomitanLanguage::~YomitanLanguage() {
  JS_FreeContext(context_);
  JS_FreeRuntime(runtime_);
}

std::string YomitanLanguage::candidates_json(const std::string& text, const std::string& language,
                                             const std::string& resolution) {
  const std::string args[] = {text, language, resolution};
  return call("candidatesJson", args, 3);
}

std::string YomitanLanguage::part_of_speech_flags_json(const std::string& language) {
  const std::string args[] = {language};
  return call("partOfSpeechFlagsJson", args, 1);
}

std::string YomitanLanguage::call(const char* function, const std::string* args, int count) {
  // Calls come from a pool of threads; QuickJS checks the stack against the thread it last saw.
  JS_UpdateStackTop(runtime_);
  JSValue global = JS_GetGlobalObject(context_);
  JSValue api = JS_GetPropertyStr(context_, global, "yomitanLang");
  JSValue target = JS_GetPropertyStr(context_, api, function);
  JSValue values[4];
  for (int i = 0; i < count; i++) {
    values[i] = JS_NewStringLen(context_, args[i].data(), args[i].size());
  }
  JSValue result = JS_Call(context_, target, api, count, values);
  for (int i = 0; i < count; i++) {
    JS_FreeValue(context_, values[i]);
  }
  JS_FreeValue(context_, target);
  JS_FreeValue(context_, api);
  JS_FreeValue(context_, global);
  if (JS_IsException(result)) {
    throw std::runtime_error(std::string(function) + ": " + take_exception(context_));
  }
  size_t length = 0;
  const char* text = JS_ToCStringLen(context_, &length, result);
  std::string out(text != nullptr ? text : "", text != nullptr ? length : 0);
  JS_FreeCString(context_, text);
  JS_FreeValue(context_, result);
  return out;
}

}  // namespace screenlate_language
