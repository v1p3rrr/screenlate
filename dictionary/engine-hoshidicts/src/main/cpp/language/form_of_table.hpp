// Form-of entries of a dictionary (`["form", "", "non-lemma", "v", 0, [["lemma", ["tag", ...]], ...]]`) kept apart
// from hoshidicts: a dictionary like Wiktionary Russian→English has 1.4 M such rows, which hoshidicts would store at
// about 160 B each. The table maps every form to (lemma, tag set, rules) entries in a few bytes.
//
// File layout (little-endian), written once at import and memory-mapped for lookups:
//   header: magic "SLFO", version, form count, block count, string count, tag set count, then the offsets of the
//           block index, the blocks, the string index, the string data, the tag set index and the tag set data;
//   blocks: forms sorted by their UTF-8 bytes, 16 per block, each written as varint shared prefix length (0 for the
//           first form of a block), varint suffix length, suffix bytes, varint entry count, then per entry the varint
//           ids of the lemma (a string), the tag set and the row's part-of-speech rules (a string); the block index
//           holds each block's offset;
//   strings: index of (count + 1) offsets into the concatenated lemmas and rules;
//   tag sets: index of (count + 1) offsets into data where each set is varint count, then varint length and bytes
//           per tag.
#pragma once

#include <cstdint>
#include <filesystem>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace screenlate_language {

struct FormOf {
  std::string lemma;
  std::vector<std::string> tags;
  // Part-of-speech rules of the form's row (Yomitan `rules`), for the condition filter of deinflections.
  std::string rules;
};

class FormOfTableBuilder {
 public:
  void add(std::string_view form, std::string_view lemma, const std::vector<std::string>& tags, std::string_view rules);

  bool empty() const { return forms_.empty(); }

  // Distinct (form, lemma, tag set, rules) entries added.
  size_t entry_count() const { return entries_; }

  size_t form_count() const { return forms_.size(); }

  // Writes the table; throws std::runtime_error on failure.
  void write(const std::filesystem::path& path) const;

 private:
  struct Item {
    uint32_t lemma;
    uint32_t tag_set;
    uint32_t rules;
    bool operator==(const Item&) const = default;
  };

  static uint32_t intern(std::unordered_map<std::string, uint32_t>& ids, std::vector<std::string>& values,
                         std::string key);

  std::unordered_map<std::string, uint32_t> string_ids_;
  std::vector<std::string> strings_;
  // Tag sets keyed by their tags joined with U+001F.
  std::unordered_map<std::string, uint32_t> tag_set_ids_;
  std::vector<std::string> tag_sets_;
  std::unordered_map<std::string, std::vector<Item>> forms_;
  size_t entries_ = 0;
};

class FormOfTable {
 public:
  // The table at [path]; null when there is no such file. Throws std::runtime_error when the file is damaged.
  static std::unique_ptr<FormOfTable> open(const std::filesystem::path& path);

  ~FormOfTable();
  FormOfTable(const FormOfTable&) = delete;
  FormOfTable& operator=(const FormOfTable&) = delete;

  // The lemmas [form] is a form of, in the order the dictionary lists them.
  std::vector<FormOf> find(std::string_view form) const;

  size_t form_count() const { return form_count_; }

 private:
  FormOfTable() = default;

  std::string_view first_form(uint32_t block) const;
  std::string_view string(uint32_t id) const;
  std::vector<std::string> tag_set(uint32_t id) const;

  const uint8_t* data_ = nullptr;
  size_t size_ = 0;
  uint32_t form_count_ = 0;
  uint32_t block_count_ = 0;
  uint32_t string_count_ = 0;
  uint32_t tag_set_count_ = 0;
  const uint8_t* block_index_ = nullptr;
  const uint8_t* blocks_ = nullptr;
  size_t blocks_size_ = 0;
  const uint8_t* string_index_ = nullptr;
  const uint8_t* string_data_ = nullptr;
  size_t string_data_size_ = 0;
  const uint8_t* tag_set_index_ = nullptr;
  const uint8_t* tag_set_data_ = nullptr;
  size_t tag_set_data_size_ = 0;
};

}  // namespace screenlate_language
