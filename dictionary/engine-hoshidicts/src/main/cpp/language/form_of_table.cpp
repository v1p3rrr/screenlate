#include "form_of_table.hpp"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace screenlate_language {
namespace {

constexpr char kMagic[4] = {'S', 'L', 'F', 'O'};
constexpr uint32_t kVersion = 1;
constexpr size_t kBlockSize = 16;
// Magic, version, four counts and six offsets.
constexpr size_t kHeaderSize = 4 + 4 + 4 * 4 + 6 * 8;
constexpr char kTagSeparator = static_cast<char>(0x1F);

void put_u32(std::vector<uint8_t>& out, uint32_t value) {
  for (int i = 0; i < 4; i++) out.push_back(static_cast<uint8_t>(value >> (8 * i)));
}

void put_u64(std::vector<uint8_t>& out, uint64_t value) {
  for (int i = 0; i < 8; i++) out.push_back(static_cast<uint8_t>(value >> (8 * i)));
}

void set_u64(std::vector<uint8_t>& out, size_t at, uint64_t value) {
  for (int i = 0; i < 8; i++) out[at + i] = static_cast<uint8_t>(value >> (8 * i));
}

void put_varint(std::vector<uint8_t>& out, uint64_t value) {
  while (value >= 0x80) {
    out.push_back(static_cast<uint8_t>(value | 0x80));
    value >>= 7;
  }
  out.push_back(static_cast<uint8_t>(value));
}

void put_bytes(std::vector<uint8_t>& out, std::string_view bytes) { out.insert(out.end(), bytes.begin(), bytes.end()); }

uint32_t checked_u32(size_t value) {
  if (value > std::numeric_limits<uint32_t>::max()) throw std::runtime_error("form-of table too large");
  return static_cast<uint32_t>(value);
}

uint32_t get_u32(const uint8_t* at) {
  uint32_t value = 0;
  for (int i = 0; i < 4; i++) value |= static_cast<uint32_t>(at[i]) << (8 * i);
  return value;
}

uint64_t get_u64(const uint8_t* at) {
  uint64_t value = 0;
  for (int i = 0; i < 8; i++) value |= static_cast<uint64_t>(at[i]) << (8 * i);
  return value;
}

// Reads bytes within [pos, end); every read checks the bounds, so a damaged file yields false instead of a crash.
struct Reader {
  const uint8_t* pos;
  const uint8_t* end;

  bool varint(uint64_t& value) {
    value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (pos >= end) return false;
      const uint8_t byte = *pos++;
      value |= static_cast<uint64_t>(byte & 0x7F) << shift;
      if ((byte & 0x80) == 0) return true;
    }
    return false;
  }

  bool bytes(uint64_t count, std::string_view& out) {
    if (static_cast<uint64_t>(end - pos) < count) return false;
    out = std::string_view(reinterpret_cast<const char*>(pos), static_cast<size_t>(count));
    pos += count;
    return true;
  }
};

}  // namespace

uint32_t FormOfTableBuilder::intern(std::unordered_map<std::string, uint32_t>& ids, std::vector<std::string>& values,
                                    std::string key) {
  auto it = ids.find(key);
  if (it != ids.end()) return it->second;
  const uint32_t id = checked_u32(values.size());
  values.push_back(key);
  ids.emplace(std::move(key), id);
  return id;
}

void FormOfTableBuilder::add(std::string_view form, std::string_view lemma, const std::vector<std::string>& tags,
                             std::string_view rules) {
  if (form.empty() || lemma.empty()) return;
  std::string joined;
  for (size_t i = 0; i < tags.size(); i++) {
    if (i > 0) joined += kTagSeparator;
    joined += tags[i];
  }
  const Item entry{
      intern(string_ids_, strings_, std::string(lemma)),
      intern(tag_set_ids_, tag_sets_, std::move(joined)),
      intern(string_ids_, strings_, std::string(rules)),
  };
  auto& entries = forms_[std::string(form)];
  if (std::find(entries.begin(), entries.end(), entry) != entries.end()) return;
  entries.push_back(entry);
  entries_++;
}

void FormOfTableBuilder::write(const std::filesystem::path& path) const {
  std::vector<const std::string*> keys;
  keys.reserve(forms_.size());
  for (const auto& [form, entries] : forms_) keys.push_back(&form);
  std::sort(keys.begin(), keys.end(), [](const std::string* a, const std::string* b) { return *a < *b; });

  std::vector<uint8_t> blocks;
  std::vector<uint32_t> block_offsets;
  for (size_t i = 0; i < keys.size(); i++) {
    const std::string& form = *keys[i];
    size_t shared = 0;
    if (i % kBlockSize == 0) {
      block_offsets.push_back(checked_u32(blocks.size()));
    } else {
      const std::string& previous = *keys[i - 1];
      const size_t limit = std::min(previous.size(), form.size());
      while (shared < limit && previous[shared] == form[shared]) shared++;
    }
    put_varint(blocks, shared);
    put_varint(blocks, form.size() - shared);
    put_bytes(blocks, std::string_view(form).substr(shared));
    const auto& entries = forms_.at(form);
    put_varint(blocks, entries.size());
    for (const auto& entry : entries) {
      put_varint(blocks, entry.lemma);
      put_varint(blocks, entry.tag_set);
      put_varint(blocks, entry.rules);
    }
  }

  std::vector<uint8_t> string_data;
  std::vector<uint32_t> string_offsets;
  for (const auto& value : strings_) {
    string_offsets.push_back(checked_u32(string_data.size()));
    put_bytes(string_data, value);
  }
  string_offsets.push_back(checked_u32(string_data.size()));

  std::vector<uint8_t> tag_set_data;
  std::vector<uint32_t> tag_set_offsets;
  for (const auto& joined : tag_sets_) {
    tag_set_offsets.push_back(checked_u32(tag_set_data.size()));
    std::vector<std::string_view> tags;
    if (!joined.empty()) {
      size_t start = 0;
      while (true) {
        const size_t next = joined.find(kTagSeparator, start);
        tags.push_back(std::string_view(joined).substr(start, next == std::string::npos ? std::string::npos : next - start));
        if (next == std::string::npos) break;
        start = next + 1;
      }
    }
    put_varint(tag_set_data, tags.size());
    for (const auto& tag : tags) {
      put_varint(tag_set_data, tag.size());
      put_bytes(tag_set_data, tag);
    }
  }
  tag_set_offsets.push_back(checked_u32(tag_set_data.size()));

  std::vector<uint8_t> out;
  out.insert(out.end(), kMagic, kMagic + 4);
  put_u32(out, kVersion);
  put_u32(out, checked_u32(keys.size()));
  put_u32(out, checked_u32(block_offsets.size()));
  put_u32(out, checked_u32(strings_.size()));
  put_u32(out, checked_u32(tag_sets_.size()));
  const size_t offsets_at = out.size();
  for (int i = 0; i < 6; i++) put_u64(out, 0);

  auto section = [&](int index, auto&& append) {
    set_u64(out, offsets_at + 8 * index, out.size());
    append();
  };
  section(0, [&] { for (uint32_t offset : block_offsets) put_u32(out, offset); });
  section(1, [&] { out.insert(out.end(), blocks.begin(), blocks.end()); });
  section(2, [&] { for (uint32_t offset : string_offsets) put_u32(out, offset); });
  section(3, [&] { out.insert(out.end(), string_data.begin(), string_data.end()); });
  section(4, [&] { for (uint32_t offset : tag_set_offsets) put_u32(out, offset); });
  section(5, [&] { out.insert(out.end(), tag_set_data.begin(), tag_set_data.end()); });

  const std::filesystem::path partial = path.string() + ".partial";
  FILE* file = std::fopen(partial.c_str(), "wb");
  if (file == nullptr) throw std::runtime_error("cannot write the form-of table");
  const bool written = std::fwrite(out.data(), 1, out.size(), file) == out.size();
  const bool closed = std::fclose(file) == 0;
  std::error_code error;
  if (!written || !closed) {
    std::filesystem::remove(partial, error);
    throw std::runtime_error("cannot write the form-of table");
  }
  std::filesystem::rename(partial, path, error);
  if (error) {
    std::filesystem::remove(partial, error);
    throw std::runtime_error("cannot write the form-of table");
  }
}

std::unique_ptr<FormOfTable> FormOfTable::open(const std::filesystem::path& path) {
  const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
  if (fd < 0) return nullptr;
  struct stat info {};
  if (::fstat(fd, &info) != 0 || info.st_size < static_cast<off_t>(kHeaderSize)) {
    ::close(fd);
    throw std::runtime_error("damaged form-of table");
  }
  const size_t size = static_cast<size_t>(info.st_size);
  void* mapped = ::mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
  ::close(fd);
  if (mapped == MAP_FAILED) throw std::runtime_error("cannot map the form-of table");

  std::unique_ptr<FormOfTable> table(new FormOfTable());
  table->data_ = static_cast<const uint8_t*>(mapped);
  table->size_ = size;
  const uint8_t* data = table->data_;
  if (std::memcmp(data, kMagic, 4) != 0 || get_u32(data + 4) != kVersion) {
    throw std::runtime_error("damaged form-of table");
  }
  table->form_count_ = get_u32(data + 8);
  table->block_count_ = get_u32(data + 12);
  table->string_count_ = get_u32(data + 16);
  table->tag_set_count_ = get_u32(data + 20);
  uint64_t offsets[7];
  for (int i = 0; i < 6; i++) offsets[i] = get_u64(data + 24 + 8 * i);
  offsets[6] = size;
  for (int i = 0; i < 6; i++) {
    if (offsets[i] < kHeaderSize || offsets[i] > offsets[i + 1]) throw std::runtime_error("damaged form-of table");
  }
  if (offsets[1] - offsets[0] != 4ull * table->block_count_ ||
      offsets[3] - offsets[2] != 4ull * (table->string_count_ + 1ull) ||
      offsets[5] - offsets[4] != 4ull * (table->tag_set_count_ + 1ull) ||
      table->block_count_ != (table->form_count_ + kBlockSize - 1) / kBlockSize) {
    throw std::runtime_error("damaged form-of table");
  }
  table->block_index_ = data + offsets[0];
  table->blocks_ = data + offsets[1];
  table->blocks_size_ = offsets[2] - offsets[1];
  table->string_index_ = data + offsets[2];
  table->string_data_ = data + offsets[3];
  table->string_data_size_ = offsets[4] - offsets[3];
  table->tag_set_index_ = data + offsets[4];
  table->tag_set_data_ = data + offsets[5];
  table->tag_set_data_size_ = offsets[6] - offsets[5];
  // The last index entries hold the data sizes, so a cut file shows here.
  if (get_u32(table->string_index_ + 4ull * table->string_count_) != table->string_data_size_ ||
      get_u32(table->tag_set_index_ + 4ull * table->tag_set_count_) != table->tag_set_data_size_) {
    throw std::runtime_error("damaged form-of table");
  }
  return table;
}

FormOfTable::~FormOfTable() {
  if (data_ != nullptr) ::munmap(const_cast<uint8_t*>(data_), size_);
}

std::string_view FormOfTable::first_form(uint32_t block) const {
  const uint32_t offset = get_u32(block_index_ + 4ull * block);
  if (offset >= blocks_size_) return {};
  Reader reader{blocks_ + offset, blocks_ + blocks_size_};
  uint64_t shared = 0;
  uint64_t length = 0;
  std::string_view form;
  if (!reader.varint(shared) || shared != 0 || !reader.varint(length) || !reader.bytes(length, form)) return {};
  return form;
}

std::string_view FormOfTable::string(uint32_t id) const {
  if (id >= string_count_) return {};
  const uint32_t start = get_u32(string_index_ + 4ull * id);
  const uint32_t end = get_u32(string_index_ + 4ull * (id + 1));
  if (start > end || end > string_data_size_) return {};
  return std::string_view(reinterpret_cast<const char*>(string_data_) + start, end - start);
}

std::vector<std::string> FormOfTable::tag_set(uint32_t id) const {
  std::vector<std::string> tags;
  if (id >= tag_set_count_) return tags;
  const uint32_t start = get_u32(tag_set_index_ + 4ull * id);
  const uint32_t end = get_u32(tag_set_index_ + 4ull * (id + 1));
  if (start > end || end > tag_set_data_size_) return tags;
  Reader reader{tag_set_data_ + start, tag_set_data_ + end};
  uint64_t count = 0;
  if (!reader.varint(count)) return tags;
  for (uint64_t i = 0; i < count; i++) {
    uint64_t length = 0;
    std::string_view tag;
    if (!reader.varint(length) || !reader.bytes(length, tag)) break;
    tags.emplace_back(tag);
  }
  return tags;
}

std::vector<FormOf> FormOfTable::find(std::string_view form) const {
  std::vector<FormOf> result;
  if (block_count_ == 0 || form.empty()) return result;
  // The last block whose first form is not after [form].
  uint32_t low = 0;
  uint32_t high = block_count_;
  while (high - low > 1) {
    const uint32_t middle = low + (high - low) / 2;
    if (first_form(middle) <= form) {
      low = middle;
    } else {
      high = middle;
    }
  }
  const uint32_t offset = get_u32(block_index_ + 4ull * low);
  if (offset >= blocks_size_) return result;
  Reader reader{blocks_ + offset, blocks_ + blocks_size_};
  std::string current;
  const size_t in_block = std::min<size_t>(kBlockSize, form_count_ - static_cast<size_t>(low) * kBlockSize);
  for (size_t i = 0; i < in_block; i++) {
    uint64_t shared = 0;
    uint64_t length = 0;
    std::string_view suffix;
    uint64_t count = 0;
    if (!reader.varint(shared) || shared > current.size() || !reader.varint(length) || !reader.bytes(length, suffix) ||
        !reader.varint(count)) {
      return result;
    }
    current.resize(static_cast<size_t>(shared));
    current.append(suffix);
    const bool match = current == form;
    for (uint64_t e = 0; e < count; e++) {
      uint64_t lemma_id = 0;
      uint64_t tag_set_id = 0;
      uint64_t rules_id = 0;
      if (!reader.varint(lemma_id) || !reader.varint(tag_set_id) || !reader.varint(rules_id)) return result;
      if (!match) continue;
      const std::string_view lemma = string(static_cast<uint32_t>(lemma_id));
      if (lemma.empty()) continue;
      result.push_back({std::string(lemma), tag_set(static_cast<uint32_t>(tag_set_id)),
                        std::string(string(static_cast<uint32_t>(rules_id)))});
    }
    if (match || current > form) return result;
  }
  return result;
}

}  // namespace screenlate_language
