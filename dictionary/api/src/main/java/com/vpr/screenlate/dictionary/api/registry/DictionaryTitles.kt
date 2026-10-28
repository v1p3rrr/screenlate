package com.vpr.screenlate.dictionary.api.registry

/** Dictionary titles without revision marks such as `[2026-01-04]` or `(v1.2)`, for matching across versions. */
fun dictionaryKey(title: String): String = title
    .replace(Regex("""\s*[\[(（][^\])）]*\d[^\])）]*[\])）]\s*"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()
    .lowercase()

/**
 * Ends the title of a dictionary imported for its inflected forms only ([com.vpr.screenlate.dictionary.api.DictionaryEngine.import]),
 * which tells it apart from the whole dictionary it was cut from; the native importer appends it.
 */
const val FORMS_ONLY_TITLE_SUFFIX = " (forms)"

/** Whether [title] is that of a dictionary imported for its forms only. */
fun isFormsOnly(title: String): Boolean = title.endsWith(FORMS_ONLY_TITLE_SUFFIX)

/**
 * The one dictionary of [all] with [kind] whose title differs from [title] just by its revision mark. When there are
 * several, an update of a [bundled] dictionary takes the bundled copy, the last imported of several, so it does not
 * add another.
 */
internal fun sameDictionary(all: List<DictionaryEntity>, title: String, kind: DictionaryKind, bundled: Boolean): DictionaryEntity? {
    val same = all.filter { it.kind == kind && dictionaryKey(it.title) == dictionaryKey(title) }
    return same.singleOrNull()
        ?: if (bundled) same.filter { it.bundled }.maxWithOrNull(compareBy({ it.importedAt }, { it.id })) else null
}
