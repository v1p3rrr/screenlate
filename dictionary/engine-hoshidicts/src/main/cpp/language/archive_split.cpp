#include "archive_split.hpp"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <libdeflate.h>

#include <cstdio>
#include <cstring>
#include <filesystem>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <tuple>
#include <vector>

#include <glaze/glaze.hpp>

namespace screenlate_language {
// glaze reflection needs types with linkage.
namespace archive_split_json {

struct IndexLanguage {
  std::optional<std::string> sourceLanguage;
};

}  // namespace archive_split_json

namespace {

using archive_split_json::IndexLanguage;

struct Entry {
  std::string name;
  uint16_t flags = 0;
  uint16_t method = 0;
  uint32_t crc = 0;
  uint32_t compressed_size = 0;
  uint32_t uncompressed_size = 0;
  size_t data_offset = 0;
};

class MappedFile {
 public:
  explicit MappedFile(const std::string& path) {
    const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    struct stat info {};
    if (::fstat(fd, &info) == 0 && info.st_size > 0) {
      void* mapped = ::mmap(nullptr, static_cast<size_t>(info.st_size), PROT_READ, MAP_PRIVATE, fd, 0);
      if (mapped != MAP_FAILED) {
        data_ = static_cast<const uint8_t*>(mapped);
        size_ = static_cast<size_t>(info.st_size);
      }
    }
    ::close(fd);
  }
  ~MappedFile() {
    if (data_ != nullptr) ::munmap(const_cast<uint8_t*>(data_), size_);
  }
  MappedFile(const MappedFile&) = delete;
  MappedFile& operator=(const MappedFile&) = delete;

  const uint8_t* data() const { return data_; }
  size_t size() const { return size_; }

 private:
  const uint8_t* data_ = nullptr;
  size_t size_ = 0;
};

template <typename T>
T read_at(const uint8_t* base, size_t offset) {
  T value;
  std::memcpy(&value, base + offset, sizeof(T));
  return value;
}

// The central directory of a classic (not ZIP64) archive; nullopt when it cannot be read.
std::optional<std::vector<Entry>> read_entries(const MappedFile& file) {
  const uint8_t* base = file.data();
  const size_t size = file.size();
  if (base == nullptr || size < 22) return std::nullopt;
  size_t eocd = size - 22;
  while (read_at<uint32_t>(base, eocd) != 0x06054b50) {
    if (eocd == 0 || size - eocd > 22 + 0xFFFF) return std::nullopt;
    eocd--;
  }
  const uint16_t count = read_at<uint16_t>(base, eocd + 10);
  const uint32_t directory = read_at<uint32_t>(base, eocd + 16);
  if (count == 0xFFFF || directory == 0xFFFFFFFF) return std::nullopt;
  std::vector<Entry> entries;
  size_t pos = directory;
  for (uint16_t i = 0; i < count; i++) {
    if (pos + 46 > size || read_at<uint32_t>(base, pos) != 0x02014b50) return std::nullopt;
    Entry entry;
    entry.flags = read_at<uint16_t>(base, pos + 8);
    entry.method = read_at<uint16_t>(base, pos + 10);
    entry.crc = read_at<uint32_t>(base, pos + 16);
    entry.compressed_size = read_at<uint32_t>(base, pos + 20);
    entry.uncompressed_size = read_at<uint32_t>(base, pos + 24);
    const uint16_t name_length = read_at<uint16_t>(base, pos + 28);
    const uint16_t extra_length = read_at<uint16_t>(base, pos + 30);
    const uint16_t comment_length = read_at<uint16_t>(base, pos + 32);
    const uint32_t header = read_at<uint32_t>(base, pos + 42);
    if (pos + 46 + name_length > size) return std::nullopt;
    entry.name.assign(reinterpret_cast<const char*>(base + pos + 46), name_length);
    if (entry.compressed_size == 0xFFFFFFFF || entry.uncompressed_size == 0xFFFFFFFF || header == 0xFFFFFFFF) {
      return std::nullopt;
    }
    if (static_cast<size_t>(header) + 30 > size || read_at<uint32_t>(base, header) != 0x04034b50) return std::nullopt;
    entry.data_offset = header + 30 + read_at<uint16_t>(base, header + 26) + read_at<uint16_t>(base, header + 28);
    if (entry.data_offset > size || size - entry.data_offset < entry.compressed_size) return std::nullopt;
    if (entry.method != 0 && entry.method != 8) return std::nullopt;
    if (entry.method == 0 && entry.compressed_size != entry.uncompressed_size) return std::nullopt;
    entries.push_back(std::move(entry));
    pos += 46 + name_length + extra_length + comment_length;
  }
  return entries;
}

std::optional<std::string> inflate(const MappedFile& file, const Entry& entry, libdeflate_decompressor* decompressor) {
  std::string out(entry.uncompressed_size, ' ');
  const uint8_t* source = file.data() + entry.data_offset;
  if (entry.method == 0) {
    std::memcpy(out.data(), source, entry.uncompressed_size);
    return out;
  }
  if (libdeflate_deflate_decompress(decompressor, source, entry.compressed_size, out.data(), out.size(), nullptr) !=
      LIBDEFLATE_SUCCESS) {
    return std::nullopt;
  }
  return out;
}

bool is_space(char c) { return c == ' ' || c == 9 || c == 10 || c == 13; }

bool is_array(std::string_view json) {
  size_t i = 0;
  while (i < json.size() && is_space(json[i])) i++;
  return i < json.size() && json[i] == '[';
}

// Whether a term bank may hold form-of items: each ends its tag list and itself with "]]", which rows without them
// have only at the bank's end. Saves parsing the banks of dictionaries without forms, Japanese ones among them.
bool may_hold_form_of(const std::string& content) {
  size_t end = content.size();
  while (end > 0 && is_space(content[end - 1])) end--;
  if (end == 0 || content[end - 1] != ']') return false;
  end--;
  for (size_t pos = content.find(']'); pos < end; pos = content.find(']', pos + 1)) {
    size_t next = pos + 1;
    while (next < end && is_space(content[next])) next++;
    if (next < end && content[next] == ']') return true;
  }
  return false;
}

bool is_term_bank(const std::string& name) {
  return name.starts_with("term_bank_") && name.ends_with(".json") && name.find('/') == std::string::npos;
}

class ArchiveWriter {
 public:
  explicit ArchiveWriter(const std::string& path) : file_(std::fopen(path.c_str(), "wb")) {
    if (file_ == nullptr) throw std::runtime_error("cannot write the split archive");
  }
  ~ArchiveWriter() {
    if (file_ != nullptr) std::fclose(file_);
  }
  ArchiveWriter(const ArchiveWriter&) = delete;
  ArchiveWriter& operator=(const ArchiveWriter&) = delete;

  void add(const Entry& entry, const uint8_t* data) {
    if (offset_ > 0xFFFFFFFFull) throw std::runtime_error("split archive too large");
    Written written{entry, static_cast<uint32_t>(offset_)};
    // Sizes are written in the local header, so a data descriptor never follows.
    written.entry.flags = static_cast<uint16_t>(entry.flags & 0x0800);
    std::vector<uint8_t> header;
    put32(header, 0x04034b50);
    put16(header, 20);
    put16(header, written.entry.flags);
    put16(header, entry.method);
    put16(header, 0);
    put16(header, 0x21);  // 1980-01-01
    put32(header, entry.crc);
    put32(header, entry.compressed_size);
    put32(header, entry.uncompressed_size);
    put16(header, static_cast<uint16_t>(entry.name.size()));
    put16(header, 0);
    header.insert(header.end(), entry.name.begin(), entry.name.end());
    write(header.data(), header.size());
    write(data, entry.compressed_size);
    entries_.push_back(std::move(written));
  }

  void finish() {
    const uint64_t directory = offset_;
    std::vector<uint8_t> out;
    for (const auto& [entry, local] : entries_) {
      put32(out, 0x02014b50);
      put16(out, 20);
      put16(out, 20);
      put16(out, entry.flags);
      put16(out, entry.method);
      put16(out, 0);
      put16(out, 0x21);
      put32(out, entry.crc);
      put32(out, entry.compressed_size);
      put32(out, entry.uncompressed_size);
      put16(out, static_cast<uint16_t>(entry.name.size()));
      put16(out, 0);
      put16(out, 0);
      put16(out, 0);
      put16(out, 0);
      put32(out, 0);
      put32(out, local);
      out.insert(out.end(), entry.name.begin(), entry.name.end());
    }
    if (entries_.size() > 0xFFFE || directory + out.size() > 0xFFFFFFFFull) {
      throw std::runtime_error("split archive too large");
    }
    const uint32_t directory_size = static_cast<uint32_t>(out.size());
    put32(out, 0x06054b50);
    put16(out, 0);
    put16(out, 0);
    put16(out, static_cast<uint16_t>(entries_.size()));
    put16(out, static_cast<uint16_t>(entries_.size()));
    put32(out, directory_size);
    put32(out, static_cast<uint32_t>(directory));
    put16(out, 0);
    write(out.data(), out.size());
    const bool closed = std::fclose(file_) == 0;
    file_ = nullptr;
    if (!closed) throw std::runtime_error("cannot write the split archive");
  }

 private:
  struct Written {
    Entry entry;
    uint32_t local;
  };

  static void put16(std::vector<uint8_t>& out, uint16_t value) {
    out.push_back(static_cast<uint8_t>(value));
    out.push_back(static_cast<uint8_t>(value >> 8));
  }
  static void put32(std::vector<uint8_t>& out, uint32_t value) {
    for (int i = 0; i < 4; i++) out.push_back(static_cast<uint8_t>(value >> (8 * i)));
  }

  void write(const void* data, size_t size) {
    if (size > 0 && std::fwrite(data, 1, size, file_) != size) throw std::runtime_error("cannot write the split archive");
    offset_ += size;
  }

  FILE* file_;
  uint64_t offset_ = 0;
  std::vector<Written> entries_;
};

struct Compressors {
  libdeflate_decompressor* decompressor = libdeflate_alloc_decompressor();
  libdeflate_compressor* compressor = libdeflate_alloc_compressor(1);
  ~Compressors() {
    if (decompressor != nullptr) libdeflate_free_decompressor(decompressor);
    if (compressor != nullptr) libdeflate_free_compressor(compressor);
  }
};

// The rows of one term bank without their form-of items, which go into [split]; nullopt when the bank has none (or
// cannot be read, which hoshidicts then reports).
std::optional<std::string> split_bank(const std::string& content, ArchiveSplit& split) {
  std::vector<glz::raw_json_view> rows;
  if (glz::read_json(rows, content)) return std::nullopt;
  std::string out = "[";
  bool changed = false;
  bool first = true;
  auto append = [&](std::string_view row) {
    if (!first) out += ',';
    out += row;
    first = false;
  };
  for (const auto& row : rows) {
    std::vector<glz::raw_json_view> fields;
    std::vector<glz::raw_json_view> items;
    if (glz::read_json(fields, row.str) || fields.size() < 6 || glz::read_json(items, fields[5].str)) {
      append(row.str);
      continue;
    }
    std::vector<std::tuple<std::string, std::vector<std::string>>> form_of;
    std::vector<std::string_view> definitions;
    for (const auto& item : items) {
      std::tuple<std::string, std::vector<std::string>> parsed;
      if (is_array(item.str) && !glz::read_json(parsed, item.str) && !std::get<0>(parsed).empty()) {
        form_of.push_back(std::move(parsed));
      } else {
        definitions.push_back(item.str);
      }
    }
    std::string expression;
    if (form_of.empty() || glz::read_json(expression, fields[0].str) || expression.empty()) {
      append(row.str);
      continue;
    }
    changed = true;
    std::string rules;
    if (glz::read_json(rules, fields[3].str)) rules.clear();
    for (const auto& [lemma, tags] : form_of) split.table.add(expression, lemma, tags, rules);
    if (definitions.empty()) {
      split.form_rows++;
      continue;
    }
    split.mixed_rows++;
    std::string rebuilt = "[";
    for (size_t i = 0; i < fields.size(); i++) {
      if (i > 0) rebuilt += ',';
      if (i != 5) {
        rebuilt += fields[i].str;
        continue;
      }
      rebuilt += '[';
      for (size_t d = 0; d < definitions.size(); d++) {
        if (d > 0) rebuilt += ',';
        rebuilt += definitions[d];
      }
      rebuilt += ']';
    }
    rebuilt += ']';
    append(rebuilt);
  }
  if (!changed) return std::nullopt;
  out += ']';
  return out;
}

}  // namespace

ArchiveSplit split_form_of(const std::string& archive, const std::string& output) {
  ArchiveSplit split;
  MappedFile file(archive);
  const auto entries = read_entries(file);
  if (!entries) return split;
  Compressors compressors;
  if (compressors.decompressor == nullptr || compressors.compressor == nullptr) return split;

  for (const auto& entry : *entries) {
    if (entry.name != "index.json") continue;
    const auto content = inflate(file, entry, compressors.decompressor);
    IndexLanguage index;
    if (content && !glz::read<glz::opts{.error_on_unknown_keys = false}>(index, *content) &&
        index.sourceLanguage == "ja") {
      return split;
    }
  }

  std::error_code error;
  try {
    // Written from the first bank that changes; until then nothing is written.
    std::unique_ptr<ArchiveWriter> writer;
    size_t copied = 0;
    for (size_t i = 0; i < entries->size(); i++) {
      const Entry& entry = (*entries)[i];
      if (!is_term_bank(entry.name)) continue;
      const auto content = inflate(file, entry, compressors.decompressor);
      const auto rest = content && may_hold_form_of(*content) ? split_bank(*content, split) : std::nullopt;
      if (!rest) continue;
      if (!writer) writer = std::make_unique<ArchiveWriter>(output);
      for (; copied < i; copied++) writer->add((*entries)[copied], file.data() + (*entries)[copied].data_offset);
      Entry changed = entry;
      changed.method = 8;
      changed.uncompressed_size = static_cast<uint32_t>(rest->size());
      changed.crc = libdeflate_crc32(0, rest->data(), rest->size());
      std::vector<uint8_t> compressed(libdeflate_deflate_compress_bound(compressors.compressor, rest->size()));
      const size_t size = libdeflate_deflate_compress(compressors.compressor, rest->data(), rest->size(),
                                                      compressed.data(), compressed.size());
      if (size == 0) throw std::runtime_error("cannot compress a term bank");
      changed.compressed_size = static_cast<uint32_t>(size);
      writer->add(changed, compressed.data());
      copied = i + 1;
    }
    if (!writer) return split;
    for (; copied < entries->size(); copied++) writer->add((*entries)[copied], file.data() + (*entries)[copied].data_offset);
    writer->finish();
  } catch (...) {
    std::filesystem::remove(output, error);
    throw;
  }
  if (split.table.empty()) {
    std::filesystem::remove(output, error);
    return split;
  }
  split.written = true;
  return split;
}

}  // namespace screenlate_language
