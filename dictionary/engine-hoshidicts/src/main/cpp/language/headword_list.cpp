#include "headword_list.hpp"

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

constexpr char kMagic[4] = {'S', 'L', 'H', 'W'};
constexpr uint32_t kVersion = 1;
// Magic, version and count.
constexpr size_t kHeaderSize = 4 + 4 + 4;

void put_u32(std::vector<uint8_t>& out, uint32_t value) {
  for (int i = 0; i < 4; i++) out.push_back(static_cast<uint8_t>(value >> (8 * i)));
}

uint32_t get_u32(const uint8_t* at) {
  uint32_t value = 0;
  for (int i = 0; i < 4; i++) value |= static_cast<uint32_t>(at[i]) << (8 * i);
  return value;
}

uint32_t checked_u32(size_t value) {
  if (value > std::numeric_limits<uint32_t>::max()) throw std::runtime_error("headword list too large");
  return static_cast<uint32_t>(value);
}

}  // namespace

void HeadwordList::write(const std::filesystem::path& path, std::vector<std::string> headwords) {
  std::sort(headwords.begin(), headwords.end());
  headwords.erase(std::unique(headwords.begin(), headwords.end()), headwords.end());
  std::erase_if(headwords, [](const std::string& headword) { return headword.empty(); });

  std::vector<uint8_t> out;
  out.insert(out.end(), kMagic, kMagic + 4);
  put_u32(out, kVersion);
  put_u32(out, checked_u32(headwords.size()));
  size_t offset = 0;
  for (const auto& headword : headwords) {
    put_u32(out, checked_u32(offset));
    offset += headword.size();
  }
  put_u32(out, checked_u32(offset));
  for (const auto& headword : headwords) out.insert(out.end(), headword.begin(), headword.end());

  const std::filesystem::path partial = path.string() + ".partial";
  FILE* file = std::fopen(partial.c_str(), "wb");
  if (file == nullptr) throw std::runtime_error("cannot write the headword list");
  const bool written = std::fwrite(out.data(), 1, out.size(), file) == out.size();
  const bool closed = std::fclose(file) == 0;
  std::error_code error;
  if (!written || !closed) {
    std::filesystem::remove(partial, error);
    throw std::runtime_error("cannot write the headword list");
  }
  std::filesystem::rename(partial, path, error);
  if (error) {
    std::filesystem::remove(partial, error);
    throw std::runtime_error("cannot write the headword list");
  }
}

std::unique_ptr<HeadwordList> HeadwordList::open(const std::filesystem::path& path) {
  const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
  if (fd < 0) return nullptr;
  struct stat info {};
  if (::fstat(fd, &info) != 0 || info.st_size < static_cast<off_t>(kHeaderSize + 4)) {
    ::close(fd);
    throw std::runtime_error("damaged headword list");
  }
  const size_t size = static_cast<size_t>(info.st_size);
  void* mapped = ::mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
  ::close(fd);
  if (mapped == MAP_FAILED) throw std::runtime_error("cannot map the headword list");

  std::unique_ptr<HeadwordList> list(new HeadwordList());
  list->data_ = static_cast<const uint8_t*>(mapped);
  list->size_ = size;
  const uint8_t* data = list->data_;
  if (std::memcmp(data, kMagic, 4) != 0 || get_u32(data + 4) != kVersion) throw std::runtime_error("damaged headword list");
  list->count_ = get_u32(data + 8);
  const uint64_t words_at = kHeaderSize + 4ull * (list->count_ + 1ull);
  if (words_at > size) throw std::runtime_error("damaged headword list");
  list->index_ = data + kHeaderSize;
  list->words_ = data + words_at;
  list->words_size_ = size - static_cast<size_t>(words_at);
  // The last offset holds the data size, so a cut file shows here.
  if (get_u32(list->index_ + 4ull * list->count_) != list->words_size_) throw std::runtime_error("damaged headword list");
  return list;
}

HeadwordList::~HeadwordList() {
  if (data_ != nullptr) ::munmap(const_cast<uint8_t*>(data_), size_);
}

std::string_view HeadwordList::at(uint32_t index) const {
  const uint32_t start = get_u32(index_ + 4ull * index);
  const uint32_t end = get_u32(index_ + 4ull * (index + 1));
  if (start > end || end > words_size_) return {};
  return std::string_view(reinterpret_cast<const char*>(words_) + start, end - start);
}

bool HeadwordList::contains(std::string_view text) const {
  if (text.empty()) return false;
  uint32_t low = 0;
  uint32_t high = count_;
  while (low < high) {
    const uint32_t middle = low + (high - low) / 2;
    const std::string_view word = at(middle);
    if (word == text) return true;
    if (word < text) {
      low = middle + 1;
    } else {
      high = middle;
    }
  }
  return false;
}

}  // namespace screenlate_language
