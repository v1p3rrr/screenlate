package com.vpr.screenlate.overlay.fonts

/**
 * Reads the user's custom CSS the way a browser would, to report what a browser silently skips (with line
 * numbers) and to find the `font-family` declarations, so missing fonts can be reported and a fallback appended.
 */
object CssCheck {
    enum class Problem {
        UNCLOSED_COMMENT,
        UNCLOSED_STRING,

        /** A `}` without an open block. */
        UNEXPECTED_BRACE,

        /** A block that is never closed. */
        UNCLOSED_BLOCK,

        /** A declaration without `:`. */
        MISSING_COLON,

        /** The text before `:` is not a property name. */
        BAD_PROPERTY,
        EMPTY_VALUE,

        /** An empty entry in a `font-family` list, e.g. a comma too many; the whole declaration is dropped. */
        EMPTY_FONT_NAME,

        /** A declaration outside of any rule. */
        OUTSIDE_RULE,

        /** A font the phone does not have; the next font of the list is used. */
        UNKNOWN_FONT,

        /**
         * A file from the internet (`url(https://...)`, `@import "https://..."`); the detail is its host. The lookup
         * page lets stylesheets and fonts through and blocks the rest, and the server sees when a lookup shows it.
         */
        REMOTE_FILE,
    }

    /** @param detail the font name, the property or the text near the problem. */
    data class Issue(val line: Int, val problem: Problem, val detail: String = "")

    /**
     * A `font-family` declaration.
     *
     * @param names the families in order, unquoted; empty entries are left out.
     * @param end index in the CSS right after the last family (before `!important`).
     * @param appendable a fallback may be appended: the list is not a CSS-wide keyword and has no `var()`.
     */
    data class FontFamily(val line: Int, val names: List<String>, val end: Int, val appendable: Boolean)

    /**
     * @param fontFamilies the `font-family` properties of style rules; `@font-face` descriptors are not among them.
     * @param declaredFonts families the CSS defines itself with `@font-face`.
     */
    data class Result(val issues: List<Issue>, val fontFamilies: List<FontFamily>, val declaredFonts: List<String> = emptyList())

    fun analyze(css: String): Result = Reader(css).read()

    /**
     * [Problem.UNKNOWN_FONT] issues for the families that [isKnown] rejects and the CSS does not define itself, each
     * name reported once.
     */
    fun unknownFonts(result: Result, isKnown: (String) -> Boolean): List<Issue> {
        // Fonts the CSS defines count as seen, so they are never reported.
        val seen = result.declaredFonts.mapTo(HashSet()) { it.lowercase() }
        return result.fontFamilies.flatMap { declaration ->
            declaration.names
                .filter { !it.contains('(') && !isKnown(it) && seen.add(it.lowercase()) }
                .map { Issue(declaration.line, Problem.UNKNOWN_FONT, it) }
        }
    }

    /**
     * [Problem.REMOTE_FILE] issues of [css], one for each host on a line. The CSS is read as a browser reads it:
     * without comments (a comment marker inside a string is text), with escapes decoded (`\68ttps:`), and addresses
     * parsed as a browser parses them (`https:\\host` and tabs inside the address).
     */
    fun remoteFiles(css: String): List<Issue> {
        val (text, lines) = unescape(blankComments(css))
        return FILE_REFERENCE.findAll(text)
            .mapNotNull { match ->
                val url = match.value.startsWith("url", ignoreCase = true)
                val host = urlValue(text, match.range.last + 1, unquoted = url)?.let(::remoteHost) ?: return@mapNotNull null
                Issue(lines[match.range.first], Problem.REMOTE_FILE, host)
            }
            .distinct()
            .toList()
    }

    /** Appends [fallback] (a font-family list) to every appendable `font-family` declaration. */
    fun withFallback(css: String, families: List<FontFamily>, fallback: String): String {
        val out = StringBuilder(css)
        families.filter { it.appendable }.sortedByDescending { it.end }.forEach { out.insert(it.end, ", $fallback") }
        return out.toString()
    }

    /** Where a file address may follow: `url(` or `@import` (whose address is a string or a `url(`). */
    private val FILE_REFERENCE = Regex("""url\(|@import""", RegexOption.IGNORE_CASE)

    /** An absolute or protocol-relative web address; group 1 is its authority. Browsers take `\` as `/` in it. */
    private val WEB_ADDRESS = Regex("""^(?:https?:)?[/\\]{2,}([^/\\?#]*)""", RegexOption.IGNORE_CASE)

    private val PROPERTY = Regex("-{0,2}[A-Za-z_][A-Za-z0-9_-]*")
    private val CSS_WIDE_KEYWORDS = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val IMPORTANT = Regex("!\\s*important\\s*$", RegexOption.IGNORE_CASE)

    private class Reader(private val css: String) {
        private val issues = mutableListOf<Issue>()
        private val families = mutableListOf<FontFamily>()
        private val declared = mutableListOf<String>()

        /** Offsets of the line breaks, the same in the CSS and in [text]. */
        private val newlines = css.indices.filter { css[it] == '\n' }.toIntArray()

        /** The CSS with comments blanked out (newlines kept), so offsets and lines match the original. */
        private val text = blankComments(css) { issues += Issue(lineOf(it), Problem.UNCLOSED_COMMENT) }

        fun read(): Result {
            var pos = 0
            while (pos < text.length) {
                val close = items(pos, depth = 0, fontFace = false)
                pos = if (close < 0) text.length else close + 1
            }
            return Result(issues.sortedBy { it.line }, families, declared)
        }

        /**
         * Reads declarations and nested rules from [start] up to the `}` that closes this level, whose index it
         * returns; -1 at the end of the text. At the top level a stray `}` is reported and skipped.
         *
         * @param fontFace the level is the body of an `@font-face` rule, whose `font-family` names a new font.
         */
        private fun items(start: Int, depth: Int, fontFace: Boolean): Int {
            var pos = start
            while (true) {
                val stop = nextSpecial(pos)
                if (stop == text.length) {
                    segment(pos, stop, depth, fontFace)
                    return -1
                }
                when (text[stop]) {
                    ';' -> {
                        segment(pos, stop, depth, fontFace)
                        pos = stop + 1
                    }
                    '{' -> {
                        val prelude = text.substring(pos, stop).trim()
                        val close = items(stop + 1, depth + 1, fontFace = prelude.startsWith("@font-face", ignoreCase = true))
                        if (close < 0) {
                            issues += Issue(lineOf(stop), Problem.UNCLOSED_BLOCK, text.substring(pos, stop).trim().take(DETAIL))
                            return -1
                        }
                        pos = close + 1
                    }
                    else -> {
                        segment(pos, stop, depth, fontFace)
                        if (depth > 0) return stop
                        issues += Issue(lineOf(stop), Problem.UNEXPECTED_BRACE)
                        pos = stop + 1
                    }
                }
            }
        }

        private fun segment(from: Int, to: Int, depth: Int, fontFace: Boolean) {
            val first = (from until to).firstOrNull { !text[it].isWhitespace() } ?: return
            val content = text.substring(first, to).trimEnd()
            when {
                depth > 0 -> declaration(first, content, fontFace)
                !content.startsWith('@') -> issues += Issue(lineOf(first), Problem.OUTSIDE_RULE, content.take(DETAIL))
            }
        }

        private fun declaration(start: Int, content: String, fontFace: Boolean) {
            val line = lineOf(start)
            val colon = indexOutsideStrings(content, ':')
            if (colon < 0) {
                issues += Issue(line, Problem.MISSING_COLON, content.take(DETAIL))
                return
            }
            val property = content.substring(0, colon).trim()
            if (!PROPERTY.matches(property)) {
                issues += Issue(line, Problem.BAD_PROPERTY, property.take(DETAIL))
                return
            }
            val rawValue = content.substring(colon + 1)
            val value = rawValue.replace(IMPORTANT, "").trimEnd()
            if (value.isBlank()) {
                // Custom properties may be empty.
                if (!property.startsWith("--")) issues += Issue(line, Problem.EMPTY_VALUE, property)
                return
            }
            if (property.equals("font-family", ignoreCase = true) && fontFace) {
                // A descriptor: exactly one name, which the page must not extend with a fallback list.
                splitOutsideStrings(value, ',').map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let { declared += unquote(it) }
            } else if (property.equals("font-family", ignoreCase = true)) {
                val valueStart = start + colon + 1
                val leading = rawValue.length - rawValue.trimStart().length
                fontFamily(line, value.trimStart(), valueStart + leading)
            }
        }

        private fun fontFamily(line: Int, value: String, valueStart: Int) {
            val entries = splitOutsideStrings(value, ',').map { it.trim() }
            val broken = entries.any { it.isEmpty() }
            if (broken) issues += Issue(line, Problem.EMPTY_FONT_NAME, value.take(DETAIL))
            val names = entries.filter { it.isNotEmpty() }.map(::unquote)
            val appendable = !broken && !value.contains("var(", ignoreCase = true) &&
                !(names.size == 1 && names[0].lowercase() in CSS_WIDE_KEYWORDS)
            families += FontFamily(line, names, valueStart + value.length, appendable)
        }

        /** Index of the next `{`, `}` or `;` outside strings and parentheses, or the text length. */
        private fun nextSpecial(from: Int): Int {
            var i = from
            var parens = 0
            while (i < text.length) {
                when (val c = text[i]) {
                    '"', '\'' -> i = stringEnd(i, c, report = true)
                    '\\' -> i++
                    '(' -> parens++
                    ')' -> if (parens > 0) parens--
                    '{', '}' -> return i
                    ';' -> if (parens == 0) return i
                }
                i++
            }
            return text.length
        }

        /** Index of the closing quote of the string opened at [open], or of the line end if it is not closed. */
        private fun stringEnd(open: Int, quote: Char, report: Boolean): Int {
            var i = open + 1
            while (i < text.length) {
                when (text[i]) {
                    '\\' -> i++
                    quote -> return i
                    '\n' -> {
                        if (report) issues += Issue(lineOf(open), Problem.UNCLOSED_STRING)
                        return i - 1
                    }
                }
                i++
            }
            if (report) issues += Issue(lineOf(open), Problem.UNCLOSED_STRING)
            return text.length
        }

        /** The 1-based line of [index]: one more than the line breaks before it. */
        private fun lineOf(index: Int): Int {
            val found = newlines.binarySearch(index)
            return (if (found >= 0) found else -found - 1) + 1
        }
    }

    /**
     * [css] with comments blanked out (line breaks kept, so offsets and lines stay); comment markers inside strings
     * and escaped characters are text. [onUnclosed] gets the offset of a comment that is never closed.
     */
    private fun blankComments(css: String, onUnclosed: (Int) -> Unit = {}): String {
        val out = StringBuilder(css)
        var i = 0
        while (i < css.length) {
            when {
                css[i] == '\\' -> i++
                css[i] == '"' || css[i] == '\'' -> i = skipRawString(css, i)
                css.startsWith("/*", i) -> {
                    val end = css.indexOf("*/", i + 2)
                    val stop = if (end < 0) css.length else end + 2
                    if (end < 0) onUnclosed(i)
                    for (j in i until stop) if (out[j] != '\n') out.setCharAt(j, ' ')
                    i = stop - 1
                }
            }
            i++
        }
        return out.toString()
    }

    /** Skips a string in the raw CSS (comment markers inside strings are text); problems are reported later. */
    private fun skipRawString(css: String, open: Int): Int {
        var i = open + 1
        while (i < css.length && css[i] != css[open] && css[i] != '\n') {
            if (css[i] == '\\') i++
            i++
        }
        return if (i < css.length && css[i] == '\n') i - 1 else i
    }

    /**
     * [text] with CSS escapes replaced by the characters they stand for, and the 1-based line in [text] of each
     * character of the result. An escaped line break (a continued string) is dropped.
     */
    private fun unescape(text: String): Pair<String, IntArray> {
        val out = StringBuilder(text.length)
        val lines = IntArray(text.length)
        var line = 1
        var i = 0
        while (i < text.length) {
            val start = line
            val c = text[i]
            if (c != '\\' || i + 1 == text.length) {
                out.append(c)
                if (c == '\n') line++
                i++
            } else if (text[i + 1] == '\n') {
                line++
                i += 2
            } else if (text[i + 1].isHexDigit()) {
                var end = i + 1
                while (end < text.length && end - i <= MAX_HEX_DIGITS && text[end].isHexDigit()) end++
                val code = text.substring(i + 1, end).toInt(HEX)
                out.appendCodePoint(if (code == 0 || code in SURROGATES || code > Character.MAX_CODE_POINT) REPLACEMENT else code)
                if (end < text.length && text[end] in CSS_WHITESPACE) {
                    if (text[end] == '\n') line++
                    end++
                }
                i = end
            } else {
                out.append(text[i + 1])
                i += 2
            }
            for (j in (out.length - 1) downTo 0) {
                if (lines[j] != 0) break
                lines[j] = start
            }
        }
        return out.toString() to lines
    }

    /**
     * The address after `url(` or `@import` at [start] of decoded CSS: a quoted string, or with [unquoted] the text up
     * to `)`; null when none follows (an `@import url(...)`, whose `url(` is read on its own).
     */
    private fun urlValue(text: String, start: Int, unquoted: Boolean): String? {
        var i = start
        while (i < text.length && text[i] in CSS_WHITESPACE) i++
        if (i == text.length) return null
        val quote = text[i]
        if (quote == '"' || quote == '\'') {
            val end = (i + 1 until text.length).firstOrNull { text[it] == quote || text[it] == '\n' } ?: text.length
            return text.substring(i + 1, end)
        }
        if (!unquoted) return null
        val end = (i until text.length).firstOrNull { text[it] == ')' || text[it] in CSS_WHITESPACE } ?: text.length
        return text.substring(i, end)
    }

    /** The host [address] reaches on the internet, lowercase, or null for data, local and relative addresses. */
    private fun remoteHost(address: String): String? {
        // As a browser's URL parser: spaces and control characters around it and tabs and line breaks in it go.
        val url = address.trim { it <= ' ' }.filterNot { it == '\t' || it == '\n' || it == '\r' }
        val authority = WEB_ADDRESS.find(url)?.groupValues?.get(1) ?: return null
        val hostAndPort = authority.substringAfterLast('@')
        val host = if (hostAndPort.startsWith('[')) hostAndPort.substringBefore(']') + "]" else hostAndPort.substringBefore(':')
        return host.lowercase().takeIf { it.isNotEmpty() }
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun indexOutsideStrings(text: String, target: Char): Int {
        var quote: Char? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> i++
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == target -> return i
            }
            i++
        }
        return -1
    }

    private fun splitOutsideStrings(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var rest = text
        while (true) {
            val index = indexOutsideStrings(rest, separator)
            if (index < 0) break
            parts += rest.substring(0, index)
            rest = rest.substring(index + 1)
        }
        parts += rest
        return parts
    }

    /** A quoted family name without its quotes and escapes; an unquoted one with single spaces. */
    private fun unquote(name: String): String {
        if (name.length >= 2 && (name[0] == '"' || name[0] == '\'') && name.last() == name[0]) {
            return name.substring(1, name.length - 1).replace(Regex("\\\\(.)"), "$1")
        }
        return name.split(Regex("\\s+")).joinToString(" ")
    }

    private const val DETAIL = 40
    private const val HEX = 16
    private const val MAX_HEX_DIGITS = 6
    private const val REPLACEMENT = 0xFFFD
    private val SURROGATES = 0xD800..0xDFFF
    private val CSS_WHITESPACE = charArrayOf(' ', '\t', '\n', '\r', '\u000C')
}
