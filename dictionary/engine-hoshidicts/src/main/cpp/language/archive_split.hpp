// Splits the form-of rows out of a Yomitan archive before hoshidicts imports it (see form_of_table.hpp).
#pragma once

#include <cstddef>
#include <string>

#include "form_of_table.hpp"

namespace screenlate_language {

struct ArchiveSplit {
  // Whether [output] holds the archive to import instead of the original: one without the form-of rows.
  bool written = false;
  FormOfTableBuilder table;
  // Rows that held only form-of items and were left out.
  size_t form_rows = 0;
  // Rows that kept their definitions while their form-of items went into the table.
  size_t mixed_rows = 0;
};

// Reads the term banks of the archive at [archive]; when they hold form-of items, writes an archive without them to
// [output] and returns them in the table. Only dictionaries whose index names a source language other than Japanese are
// split: Japanese lookup runs in hoshidicts, which keeps such rows, and a dictionary without a language serves it too.
// Archives this cannot read are left to hoshidicts as well.
ArchiveSplit split_form_of(const std::string& archive, const std::string& output);

}  // namespace screenlate_language
