#include "archive_split.hpp"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <libdeflate.h>
#include <utf8proc.h>

#include <algorithm>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <tuple>
#include <unordered_set>
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

// [text] without the whitespace before combining marks: an ending written apart from its word ("মুখ ে").
std::string join_marks(std::string_view text) {
  std::string out;
  const auto* bytes = reinterpret_cast<const utf8proc_uint8_t*>(text.data());
  const auto size = static_cast<utf8proc_ssize_t>(text.size());
  size_t spaces = std::string::npos;
  for (utf8proc_ssize_t pos = 0; pos < size;) {
    utf8proc_int32_t point = 0;
    utf8proc_ssize_t read = utf8proc_iterate(bytes + pos, size - pos, &point);
    if (read <= 0) read = 1;
    if (is_space(text[pos])) {
      if (spaces == std::string::npos) spaces = out.size();
    } else {
      const auto category = utf8proc_category(point);
      if (spaces != std::string::npos && (category == UTF8PROC_CATEGORY_MN || category == UTF8PROC_CATEGORY_MC)) {
        out.resize(spaces);
      }
      spaces = std::string::npos;
    }
    out.append(text.substr(pos, read));
    pos += read;
  }
  return out;
}

std::string_view trimmed(std::string_view text) {
  while (!text.empty() && is_space(text.front())) text.remove_prefix(1);
  while (!text.empty() && is_space(text.back())) text.remove_suffix(1);
  return text;
}

// The headwords a form-of row stands for. Wiktionary exports write some forms with a romanization or a note after
// them ("মাসটি (maśṭa (colloquial), maśṭi (formal))", "বইগুলো , boigulo"), two spellings apart ("ծագմանը / ծագմանն"),
// an ending apart from its word ("মুখ ে"), the classical spellings of Armenian words after a label
// ("orthography spelling գիւղ"), Tamil forms after a label and before their romanization ("past of அடித்துவிடு
// (aṭittuviṭu)"; English rows "past of flytip" have no romanization and are left as they are), or an unfilled template
// ("{{{3}}}ে"), which stands for no word. A Hebrew construct form written with the maqaf that joins it to the next word
// ("כֶּלֶב־") stands for the word without it as well.
std::vector<std::string> form_headwords(const std::string& expression) {
  std::vector<std::string> out;
  if (expression.find("{{{") != std::string::npos) return out;
  std::string_view text = expression;
  constexpr std::string_view kSpellingLabel = "orthography spelling ";
  if (text.starts_with(kSpellingLabel)) text.remove_prefix(kSpellingLabel.size());
  for (const std::string_view label : {std::string_view("past of "), std::string_view("future of ")}) {
    if (text.starts_with(label) && text.ends_with(')')) text.remove_prefix(label.size());
  }
  for (size_t i = 0; i < text.size(); i++) {
    if (!is_space(text[i])) continue;
    size_t next = i;
    while (next < text.size() && is_space(text[next])) next++;
    if (next < text.size() && (text[next] == '(' || text[next] == ',')) {
      text = text.substr(0, i);
      break;
    }
    i = next - 1;
  }
  while (!text.empty()) {
    size_t cut = text.size();
    size_t rest = text.size();
    for (size_t i = 0; i < text.size(); i++) {
      if (!is_space(text[i])) continue;
      size_t next = i;
      while (next < text.size() && is_space(text[next])) next++;
      if (next < text.size() && text[next] == '/') {
        cut = i;
        rest = next + 1;
        break;
      }
      i = next - 1;
    }
    const std::string_view part = trimmed(text.substr(0, cut));
    if (!part.empty()) out.push_back(join_marks(part));
    constexpr std::string_view kMaqaf = "־";  // U+05BE
    if (part.size() > kMaqaf.size() && part.ends_with(kMaqaf)) {
      out.push_back(join_marks(part.substr(0, part.size() - kMaqaf.size())));
    }
    text = text.substr(rest);
  }
  return out;
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

// [content], an index.json, with [kFormsOnlyTitleSuffix] after its title; its other fields stay as they are, so the
// dictionary still updates from its index URL.
std::string forms_only_index(const std::string& content) {
  std::map<std::string, glz::raw_json> index;
  std::string title;
  const auto found = glz::read_json(index, content) ? index.end() : index.find("title");
  if (found == index.end() || glz::read_json(title, found->second.str) || title.empty()) {
    throw std::runtime_error("cannot read the dictionary index");
  }
  if (!title.ends_with(kFormsOnlyTitleSuffix)) title += kFormsOnlyTitleSuffix;
  std::string quoted;
  if (glz::write_json(title, quoted)) throw std::runtime_error("cannot write the dictionary index");
  found->second.str = std::move(quoted);
  std::string out;
  if (glz::write_json(index, out)) throw std::runtime_error("cannot write the dictionary index");
  return out;
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
// cannot be read, which hoshidicts then reports). [stripper] drops the language's headword marks, if it has any.
// Whether a word of a form's tags is the name of a Wiktionary inflection template (ar-infl-a, ar-infl-an-maksura),
// which Wiktionary's Arabic exports put among the grammatical ones.
bool is_template_name(std::string_view word) {
  const size_t dash = word.find('-');
  if (dash < 2 || dash > 3 || !std::all_of(word.begin(), word.begin() + static_cast<std::ptrdiff_t>(dash),
                                           [](char c) { return c >= 'a' && c <= 'z'; })) {
    return false;
  }
  const std::string_view rest = word.substr(dash);
  return rest == "-infl" || rest.starts_with("-infl-");
}

// [tags] without template names, a tag left empty dropped.
std::vector<std::string> cleaned_tags(const std::vector<std::string>& tags) {
  std::vector<std::string> out;
  for (const auto& tag : tags) {
    if (tag.find("-infl") == std::string::npos) {
      out.push_back(tag);
      continue;
    }
    std::string kept;
    std::string_view rest = tag;
    while (!rest.empty()) {
      const size_t space = rest.find(' ');
      const std::string_view word = rest.substr(0, space);
      if (!word.empty() && !is_template_name(word)) {
        if (!kept.empty()) kept += ' ';
        kept += word;
      }
      rest = space == std::string_view::npos ? std::string_view() : rest.substr(space + 1);
    }
    if (!kept.empty()) out.push_back(std::move(kept));
  }
  return out;
}

// [tags] without the word [word], a tag left empty dropped.
std::vector<std::string> without_word(const std::vector<std::string>& tags, std::string_view word) {
  std::vector<std::string> out;
  for (const auto& tag : tags) {
    std::string kept;
    std::string_view rest = tag;
    while (!rest.empty()) {
      const size_t space = rest.find(' ');
      const std::string_view part = rest.substr(0, space);
      if (!part.empty() && part != word) {
        if (!kept.empty()) kept += ' ';
        kept += part;
      }
      rest = space == std::string_view::npos ? std::string_view() : rest.substr(space + 1);
    }
    if (!kept.empty()) out.push_back(std::move(kept));
  }
  return out;
}

// [lemma] without a gender written after it ("куќа f", "волк m"), which no headword carries.
std::string_view without_gender(std::string_view lemma) {
  if (lemma.size() > 2 && lemma[lemma.size() - 2] == ' ') {
    const char gender = lemma.back();
    if (gender == 'm' || gender == 'f' || gender == 'n') return trimmed(lemma.substr(0, lemma.size() - 2));
  }
  return lemma;
}

// [text] lowercase without any marks, for comparing words: По́-добре → по-добре, най → наи.
std::string bare(std::string_view text) {
  utf8proc_uint8_t* mapped = nullptr;
  const utf8proc_ssize_t length = utf8proc_map(
      reinterpret_cast<const utf8proc_uint8_t*>(text.data()), static_cast<utf8proc_ssize_t>(text.size()), &mapped,
      static_cast<utf8proc_option_t>(UTF8PROC_DECOMPOSE | UTF8PROC_STRIPMARK | UTF8PROC_CASEFOLD));
  if (length < 0) return std::string(text);
  std::string out(reinterpret_cast<const char*>(mapped), static_cast<size_t>(length));
  std::free(mapped);
  return out;
}

// The code points of [text] and the first of them.
std::pair<size_t, int32_t> letters_of(std::string_view text) {
  const auto* bytes = reinterpret_cast<const utf8proc_uint8_t*>(text.data());
  const auto size = static_cast<utf8proc_ssize_t>(text.size());
  size_t count = 0;
  int32_t first = -1;
  for (utf8proc_ssize_t pos = 0; pos < size;) {
    utf8proc_int32_t point = 0;
    const utf8proc_ssize_t read = utf8proc_iterate(bytes + pos, size - pos, &point);
    if (read <= 0) break;
    if (count++ == 0) first = point;
    pos += read;
  }
  return {count, first};
}

// Tags of the number and definiteness of a noun's or adjective's forms.
constexpr std::string_view kNumberTags[] = {
    "singular", "plural", "definite", "indefinite", "definite/indefinite", "count-form", "vocative",
};

// Whether [form] is a lone ending standing for a form of [lemma], from a table whose stem went missing (the bg-ru rows
// и, а, ите of революционер): three letters at most and fewer than the lemma's, another first letter, and only number
// and definiteness tags. A short word of its own has other tags or the lemma's first letter (е of съм, ова of овој).
bool bare_ending(std::string_view form, std::string_view lemma, const std::vector<std::string>& tags) {
  if (tags.empty()) return false;
  for (const auto& tag : tags) {
    std::string_view rest = tag;
    while (!rest.empty()) {
      const size_t space = rest.find(' ');
      const std::string_view word = rest.substr(0, space);
      if (!word.empty() && std::find(std::begin(kNumberTags), std::end(kNumberTags), word) == std::end(kNumberTags)) {
        return false;
      }
      rest = space == std::string_view::npos ? std::string_view() : rest.substr(space + 1);
    }
  }
  const std::string plain_form = bare(form);
  const auto [letters, first] = letters_of(plain_form);
  if (letters == 0 || letters > 3 || plain_form.find(' ') != std::string::npos) return false;
  const auto [lemma_letters, lemma_first] = letters_of(bare(lemma));
  return letters < lemma_letters && first != lemma_first;
}

// 64-bit FNV-1a of a row, to keep one of the rows a dictionary repeats word for word.
uint64_t row_hash(std::string_view row) {
  uint64_t hash = 0xcbf29ce484222325ULL;
  for (const char c : row) {
    hash ^= static_cast<unsigned char>(c);
    hash *= 0x100000001b3ULL;
  }
  return hash;
}

std::optional<std::string> split_bank(const std::string& content, ArchiveSplit& split, const MarkStripper* stripper,
                                      bool forms_only, std::unordered_set<uint64_t>& seen_rows) {
  std::vector<glz::raw_json_view> rows;
  if (glz::read_json(rows, content)) {
    // Kept as it is, the bank would bring its definitions along.
    if (forms_only) throw std::runtime_error("cannot read a term bank");
    return std::nullopt;
  }
  const bool every_headword = stripper != nullptr && stripper->every_headword();
  const bool keys_headwords = stripper != nullptr && stripper->keys_headwords();
  std::string out = "[";
  bool changed = false;
  bool first = true;
  auto append = [&](std::string_view row) {
    if (!first) out += ',';
    out += row;
    first = false;
  };
  // A row without form-of items, which a dictionary imported for its forms only leaves out.
  auto keep = [&](std::string_view row) {
    if (forms_only) {
      changed = true;
    } else {
      append(row);
    }
  };
  for (const auto& row : rows) {
    // Wiktionary's exports repeat some rows (mk-en 1,448), which would show the same definitions twice.
    if (!seen_rows.insert(row_hash(row.str)).second) {
      changed = true;
      continue;
    }
    std::vector<glz::raw_json_view> fields;
    std::vector<glz::raw_json_view> items;
    if (glz::read_json(fields, row.str) || fields.size() < 6 || glz::read_json(items, fields[5].str)) {
      keep(row.str);
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
    if ((form_of.empty() && !keys_headwords) || glz::read_json(expression, fields[0].str) || expression.empty()) {
      keep(row.str);
      continue;
    }
    std::string rules;
    if (glz::read_json(rules, fields[3].str)) rules.clear();
    const std::vector<std::string> keys = stripper != nullptr ? stripper->keys(expression) : std::vector<std::string>();
    const std::vector<std::string> folded_keys =
        stripper != nullptr ? stripper->folded_keys(expression, keys) : std::vector<std::string>();
    // Text without the marks (ΣΚΥΛΟΣ, σκυλος) finds the headword (σκύλος) as a form of itself, with no step to show;
    // folded text (fuzfa) finds it (fűzfa) after the words it spells as written.
    if (!definitions.empty()) {
      if (every_headword) {
        for (const auto& key : keys) split.table.add(key, expression, {}, rules);
      }
      for (const auto& key : folded_keys) split.table.add(key, expression, {}, rules, true);
    }
    if (form_of.empty()) {
      keep(row.str);
      continue;
    }
    changed = true;
    const bool dropped = stripper != nullptr && stripper->drops_form(expression);
    for (const auto& headword : dropped ? std::vector<std::string>() : form_headwords(expression)) {
      std::vector<std::string> other_keys;
      std::vector<std::string> other_folded_keys;
      if (headword != expression && stripper != nullptr) {
        other_keys = stripper->keys(headword);
        other_folded_keys = stripper->folded_keys(headword, other_keys);
      }
      const auto& form_keys = headword == expression ? keys : other_keys;
      const auto& form_folded_keys = headword == expression ? folded_keys : other_folded_keys;
      const bool comparative = stripper == nullptr || stripper->keeps_comparative(headword);
      for (const auto& [lemma, tags] : form_of) {
        const std::string_view named = without_gender(lemma);
        if (bare_ending(headword, named, tags)) continue;
        const std::string plain_lemma =
            stripper != nullptr && !stripper->lemmas_as_written() ? stripper->strip(named) : std::string();
        const std::string_view target = plain_lemma.empty() ? named : std::string_view(plain_lemma);
        const std::vector<std::string> shown =
            comparative ? cleaned_tags(tags) : without_word(cleaned_tags(tags), "comparative");
        split.table.add(headword, target, shown, rules);
        // Where every headword is keyed, a key is another spelling of the form, as for the headword itself above.
        for (const auto& key : form_keys) split.table.add(key, target, shown, rules, every_headword);
        for (const auto& key : form_folded_keys) split.table.add(key, target, shown, rules, true);
      }
    }
    if (definitions.empty() || forms_only) {
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

std::vector<int32_t> code_points(std::string_view text) {
  std::vector<int32_t> out;
  const auto* bytes = reinterpret_cast<const utf8proc_uint8_t*>(text.data());
  utf8proc_ssize_t pos = 0;
  const auto size = static_cast<utf8proc_ssize_t>(text.size());
  while (pos < size) {
    utf8proc_int32_t point = 0;
    const utf8proc_ssize_t read = utf8proc_iterate(bytes + pos, size - pos, &point);
    if (read <= 0) break;
    out.push_back(point);
    pos += read;
  }
  std::sort(out.begin(), out.end());
  return out;
}

bool contains(const std::vector<int32_t>& sorted, int32_t point) {
  return std::binary_search(sorted.begin(), sorted.end(), point);
}

// Code points of the Latin and Cyrillic blocks; their letters drop the marks after them when folded.
constexpr std::pair<int32_t, int32_t> kFoldedScripts[] = {
    {0x0041, 0x005A}, {0x0061, 0x007A}, {0x00C0, 0x02AF}, {0x0400, 0x052F}, {0x1C80, 0x1C8F}, {0x1E00, 0x1EFF},
    {0x2C60, 0x2C7F}, {0xA640, 0xA69F}, {0xA720, 0xA7FF}, {0xAB30, 0xAB6F}, {0xFF21, 0xFF3A}, {0xFF41, 0xFF5A},
};

// Latin and Cyrillic letters with a stroke, bar, descender or hook that Unicode does not decompose, with their base
// letters, sorted: ø ł đ ħ ı ŧ ƀ ɨ ƶ ǥ, ғ ҕ ӷ қ ҝ ҟ ҡ ӄ ң ӈ ө ұ ү ҳ ҷ ҹ ӌ ҫ ҙ ҭ ԓ and their capitals.
constexpr std::pair<int32_t, int32_t> kFoldedLetters[] = {
    {0x00D8, 0x004F}, {0x00F8, 0x006F}, {0x0110, 0x0044}, {0x0111, 0x0064}, {0x0126, 0x0048}, {0x0127, 0x0068},
    {0x0131, 0x0069}, {0x0141, 0x004C}, {0x0142, 0x006C}, {0x0166, 0x0054}, {0x0167, 0x0074}, {0x0180, 0x0062},
    {0x0197, 0x0049}, {0x01B5, 0x005A}, {0x01B6, 0x007A}, {0x01E4, 0x0047}, {0x01E5, 0x0067}, {0x0243, 0x0042},
    {0x0268, 0x0069}, {0x0492, 0x0413}, {0x0493, 0x0433}, {0x0494, 0x0413}, {0x0495, 0x0433}, {0x0498, 0x0417},
    {0x0499, 0x0437}, {0x049A, 0x041A}, {0x049B, 0x043A}, {0x049C, 0x041A}, {0x049D, 0x043A}, {0x049E, 0x041A},
    {0x049F, 0x043A}, {0x04A0, 0x041A}, {0x04A1, 0x043A}, {0x04A2, 0x041D}, {0x04A3, 0x043D}, {0x04AA, 0x0421},
    {0x04AB, 0x0441}, {0x04AC, 0x0422}, {0x04AD, 0x0442}, {0x04AE, 0x0423}, {0x04AF, 0x0443}, {0x04B0, 0x0423},
    {0x04B1, 0x0443}, {0x04B2, 0x0425}, {0x04B3, 0x0445}, {0x04B6, 0x0427}, {0x04B7, 0x0447}, {0x04B8, 0x0427},
    {0x04B9, 0x0447}, {0x04C3, 0x041A}, {0x04C4, 0x043A}, {0x04C7, 0x041D}, {0x04C8, 0x043D}, {0x04CB, 0x0427},
    {0x04CC, 0x0447}, {0x04E8, 0x041E}, {0x04E9, 0x043E}, {0x04F6, 0x0413}, {0x04F7, 0x0433}, {0x0512, 0x041B},
    {0x0513, 0x043B},
};

bool latin_or_cyrillic_letter(int32_t point) {
  const auto category = utf8proc_category(point);
  if (category < UTF8PROC_CATEGORY_LU || category > UTF8PROC_CATEGORY_LO) return false;
  return std::any_of(std::begin(kFoldedScripts), std::end(kFoldedScripts),
                     [&](const auto& range) { return point >= range.first && point <= range.second; });
}

int32_t base_letter(int32_t point) {
  const auto* end = std::end(kFoldedLetters);
  const auto* found = std::lower_bound(std::begin(kFoldedLetters), end, point,
                                       [](const auto& letter, int32_t value) { return letter.first < value; });
  return found != end && found->first == point ? found->second : point;
}

}  // namespace

std::string folded(std::string_view text) {
  if (std::all_of(text.begin(), text.end(), [](char c) { return static_cast<unsigned char>(c) < 0x80; })) return {};
  const auto* bytes = reinterpret_cast<const utf8proc_uint8_t*>(text.data());
  const auto size = static_cast<utf8proc_ssize_t>(text.size());
  // One more entry than the code points, which re-encoding in place needs for its terminating zero.
  std::vector<utf8proc_int32_t> points(text.size() + 1);
  utf8proc_ssize_t count = utf8proc_decompose(bytes, size, points.data(), static_cast<utf8proc_ssize_t>(points.size()),
                                              UTF8PROC_DECOMPOSE);
  if (count > static_cast<utf8proc_ssize_t>(points.size())) {
    points.resize(static_cast<size_t>(count) + 1);
    count = utf8proc_decompose(bytes, size, points.data(), static_cast<utf8proc_ssize_t>(points.size()),
                               UTF8PROC_DECOMPOSE);
  }
  if (count < 0) return {};
  utf8proc_ssize_t kept = 0;
  bool after_letter = false;
  bool changed = false;
  for (utf8proc_ssize_t i = 0; i < count; i++) {
    const utf8proc_int32_t point = points[static_cast<size_t>(i)];
    if (utf8proc_category(point) == UTF8PROC_CATEGORY_MN) {
      // A mark after another mark goes with the same letter.
      if (after_letter) {
        changed = true;
        continue;
      }
    } else {
      after_letter = latin_or_cyrillic_letter(point);
    }
    const int32_t base = base_letter(point);
    changed = changed || base != point;
    points[static_cast<size_t>(kept++)] = base;
  }
  if (!changed || kept == 0) return {};
  const utf8proc_ssize_t length =
      utf8proc_reencode(points.data(), kept, static_cast<utf8proc_option_t>(UTF8PROC_STABLE | UTF8PROC_COMPOSE));
  if (length < 0) return {};
  return std::string(reinterpret_cast<const char*>(points.data()), static_cast<size_t>(length));
}

MarkStripper::MarkStripper(const HeadwordMarks& marks)
    : bases_(code_points(marks.bases)),
      every_headword_(marks.every_headword),
      fold_marks_(marks.fold_marks),
      lemmas_as_written_(marks.lemmas_as_written),
      forms_in_script_(marks.forms_in_script),
      dropped_forms_(marks.dropped_forms),
      replacements_(marks.replacements.begin(), marks.replacements.end()),
      further_replacements_(marks.further_replacements.begin(), marks.further_replacements.end()) {
  marks_.push_back({code_points(marks.marks), bases_});
  for (const auto& [mark, bases] : marks.mark_bases) marks_.push_back({code_points(mark), code_points(bases)});
  if (!marks.letter_marks.empty()) {
    letters_.push_back({code_points(marks.letter_marks), code_points(marks.letter_bases)});
  }
  for (const auto& prefix : marks.comparative_prefixes) comparative_prefixes_.push_back(bare(prefix));
}

std::vector<std::string> MarkStripper::keys(std::string_view text) const {
  std::vector<std::string> out;
  auto add = [&](std::string key) {
    if (key.empty() || key == text || std::find(out.begin(), out.end(), key) != out.end()) return;
    out.push_back(std::move(key));
  };
  // The text with the replacements, then with the further ones on top of them.
  auto add_replaced = [&](std::string_view from) {
    std::string first = replaced(from, replacements_);
    const std::string further = replaced(first.empty() ? from : std::string_view(first), further_replacements_);
    add(std::move(first));
    add(further);
  };
  const std::string plain = strip(text);
  add_replaced(text);
  if (!plain.empty()) add_replaced(plain);
  add(plain);
  // Without the letters' marks too, once the others are gone and the replacements made: zmogaus for žmogaũs, zrl for
  // Slovenian žȓł.
  if (!letters_.empty()) {
    const std::string_view bare = plain.empty() ? text : std::string_view(plain);
    const std::string swapped = replaced(bare, replacements_);
    add(strip(swapped.empty() ? bare : std::string_view(swapped), letters_));
  }
  return out;
}

std::vector<std::string> MarkStripper::folded_keys(std::string_view text, const std::vector<std::string>& keys) const {
  std::vector<std::string> out;
  if (!fold_marks_) return out;
  auto add = [&](std::string key) {
    if (key.empty() || key == text || std::find(keys.begin(), keys.end(), key) != keys.end() ||
        std::find(out.begin(), out.end(), key) != out.end()) {
      return;
    }
    out.push_back(std::move(key));
  };
  add(folded(text));
  for (const auto& key : keys) add(folded(key));
  return out;
}

bool MarkStripper::drops_form(std::string_view text) const {
  if (std::find(dropped_forms_.begin(), dropped_forms_.end(), text) != dropped_forms_.end()) return true;
  if (!forms_in_script_) return false;
  const std::vector<int32_t> points = code_points(text);
  return std::none_of(points.begin(), points.end(), [&](int32_t point) { return contains(bases_, utf8proc_tolower(point)); });
}

bool MarkStripper::keeps_comparative(std::string_view form) const {
  if (comparative_prefixes_.empty()) return true;
  const std::string plain = bare(form);
  return std::any_of(comparative_prefixes_.begin(), comparative_prefixes_.end(),
                     [&](const std::string& prefix) { return plain.starts_with(prefix); });
}

std::string MarkStripper::replaced(std::string_view text, const Replacements& replacements) {
  std::string out(text);
  bool changed = false;
  for (const auto& [from, to] : replacements) {
    if (from.empty()) continue;
    for (size_t pos = out.find(from); pos != std::string::npos; pos = out.find(from, pos + to.size())) {
      out.replace(pos, from.size(), to);
      changed = true;
    }
  }
  return changed ? out : std::string();
}

std::string MarkStripper::strip(std::string_view text) const { return strip(text, marks_); }

std::string MarkStripper::strip(std::string_view text, const std::vector<MarkSet>& sets) {
  // Forms in plain ASCII are the common case and carry no marks.
  if (std::all_of(text.begin(), text.end(), [](char c) { return static_cast<unsigned char>(c) < 0x80; })) return {};
  const auto* bytes = reinterpret_cast<const utf8proc_uint8_t*>(text.data());
  const auto size = static_cast<utf8proc_ssize_t>(text.size());
  // One more entry than the code points, which re-encoding in place needs for its terminating zero.
  std::vector<utf8proc_int32_t> points(text.size() + 1);
  utf8proc_ssize_t count = utf8proc_decompose(bytes, size, points.data(), static_cast<utf8proc_ssize_t>(points.size()),
                                              UTF8PROC_DECOMPOSE);
  if (count > static_cast<utf8proc_ssize_t>(points.size())) {
    points.resize(static_cast<size_t>(count) + 1);
    count = utf8proc_decompose(bytes, size, points.data(), static_cast<utf8proc_ssize_t>(points.size()),
                               UTF8PROC_DECOMPOSE);
  }
  if (count < 0) return {};
  utf8proc_ssize_t kept = 0;
  utf8proc_int32_t base = 0;
  bool dropped = false;
  for (utf8proc_ssize_t i = 0; i < count; i++) {
    const utf8proc_int32_t point = points[static_cast<size_t>(i)];
    const auto lower = utf8proc_tolower(base);
    if (std::any_of(sets.begin(), sets.end(),
                    [&](const MarkSet& set) { return contains(set.marks, point) && contains(set.bases, lower); })) {
      dropped = true;
      continue;
    }
    // A mark after another mark goes with the same letter.
    if (utf8proc_category(point) != UTF8PROC_CATEGORY_MN) base = point;
    points[static_cast<size_t>(kept++)] = point;
  }
  if (!dropped || kept == 0) return {};
  const utf8proc_ssize_t length =
      utf8proc_reencode(points.data(), kept, static_cast<utf8proc_option_t>(UTF8PROC_STABLE | UTF8PROC_COMPOSE));
  if (length < 0) return {};
  return std::string(reinterpret_cast<const char*>(points.data()), static_cast<size_t>(length));
}

ArchiveSplit split_form_of(const std::string& archive, const std::string& output,
                           const std::map<std::string, HeadwordMarks>& marks, bool forms_only) {
  ArchiveSplit split;
  MappedFile file(archive);
  const auto entries = read_entries(file);
  // Left to hoshidicts, the whole dictionary would be imported.
  if (!entries && forms_only) throw std::runtime_error("cannot read the dictionary archive");
  if (!entries) return split;
  Compressors compressors;
  if (compressors.decompressor == nullptr || compressors.compressor == nullptr) {
    if (forms_only) throw std::runtime_error("cannot read the dictionary archive");
    return split;
  }

  // A dictionary without a source language also serves Japanese lookups, which would lose the rows moved out.
  bool other_language = false;
  std::optional<MarkStripper> stripper;
  for (const auto& entry : *entries) {
    if (entry.name != "index.json") continue;
    const auto content = inflate(file, entry, compressors.decompressor);
    IndexLanguage index;
    other_language = content && !glz::read<glz::opts{.error_on_unknown_keys = false}>(index, *content) &&
                     index.sourceLanguage && !index.sourceLanguage->empty() && *index.sourceLanguage != "ja";
    const auto language = other_language ? marks.find(*index.sourceLanguage) : marks.end();
    if (language != marks.end()) stripper.emplace(language->second);
  }
  if (!other_language && forms_only) throw std::runtime_error("the dictionary names no language of its own");
  if (!other_language) return split;
  // Rows seen so far, across term banks.
  std::unordered_set<uint64_t> seen_rows;

  if (forms_only) {
    for (const auto& entry : *entries) {
      const bool index = entry.name == "index.json";
      if (!index && !is_term_bank(entry.name)) continue;
      const auto content = inflate(file, entry, compressors.decompressor);
      if (!content) throw std::runtime_error("cannot read the dictionary archive");
      if (index) {
        split.index = forms_only_index(*content);
      } else {
        split_bank(*content, split, stripper ? &*stripper : nullptr, true, seen_rows);
      }
    }
    if (split.index.empty()) throw std::runtime_error("cannot read the dictionary index");
    if (split.table.empty()) throw std::runtime_error("the dictionary lists no word forms");
    return split;
  }

  std::error_code error;
  try {
    // Written from the first entry that changes; until then nothing is written.
    std::unique_ptr<ArchiveWriter> writer;
    size_t copied = 0;
    for (size_t i = 0; i < entries->size(); i++) {
      const Entry& entry = (*entries)[i];
      if (!is_term_bank(entry.name)) continue;
      const auto content = inflate(file, entry, compressors.decompressor);
      const bool keys_headwords = stripper && stripper->keys_headwords();
      std::optional<std::string> rest;
      if (content && (keys_headwords || may_hold_form_of(*content))) {
        rest = split_bank(*content, split, stripper ? &*stripper : nullptr, false, seen_rows);
      }
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
