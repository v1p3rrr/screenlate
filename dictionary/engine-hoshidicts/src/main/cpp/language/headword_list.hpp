// The headwords of a dictionary imported for its forms only (split_form_of's forms_only), whose definitions are left
// out: a word that is one of them has an entry in the dictionary the forms were cut from, which the rule filter of some
// languages counts as an entry of its own (`LanguageSupport.formsHeadwordsCountAsEntries`).
//
// File layout (little-endian), written once at import and memory-mapped for lookups:
//   header: magic "SLHW", version, headword count;
//   index: (count + 1) offsets into the data, the last one its size;
//   data: the headwords, distinct and sorted by their UTF-8 bytes, concatenated.
#pragma once

#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace screenlate_language {

class HeadwordList {
 public:
  // Writes [headwords], in any order and with repeats, to [path]; throws std::runtime_error on failure.
  static void write(const std::filesystem::path& path, std::vector<std::string> headwords);

  // The list at [path]; null when there is no such file. Throws std::runtime_error when the file is damaged.
  static std::unique_ptr<HeadwordList> open(const std::filesystem::path& path);

  ~HeadwordList();
  HeadwordList(const HeadwordList&) = delete;
  HeadwordList& operator=(const HeadwordList&) = delete;

  // Whether [text] is one of the headwords, as written.
  bool contains(std::string_view text) const;

  size_t size() const { return count_; }

 private:
  HeadwordList() = default;

  std::string_view at(uint32_t index) const;

  const uint8_t* data_ = nullptr;
  size_t size_ = 0;
  uint32_t count_ = 0;
  const uint8_t* index_ = nullptr;
  const uint8_t* words_ = nullptr;
  size_t words_size_ = 0;
};

}  // namespace screenlate_language
