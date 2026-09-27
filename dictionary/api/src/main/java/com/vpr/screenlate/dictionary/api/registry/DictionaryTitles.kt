package com.vpr.screenlate.dictionary.api.registry

/** Dictionary titles without revision marks such as `[2026-01-04]` or `(v1.2)`, for matching across versions. */
fun dictionaryKey(title: String): String = title
    .replace(Regex("""\s*[\[(（][^\])）]*\d[^\])）]*[\])）]\s*"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()
    .lowercase()
