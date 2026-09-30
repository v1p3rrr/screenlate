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
     * CRLF, CR and form feed are line breaks, comments go (a comment marker inside a string or an unquoted `url(...)`
     * is text), escapes are decoded (`\68ttps:`), a bare string of an `image-set()` is an address as much as
     * a `url(...)` is, and addresses are parsed as a browser parses them (`https:\\host` and tabs in the address).
     */
    fun remoteFiles(css: String): List<Issue> {
        val decoded = unescape(blankComments(css.replace("\r\n", "\n").replace('\r', '\n').replace('\u000C', '\n')))
        return FILE_REFERENCE.findAll(decoded.text)
            .flatMap { match ->
                val line = decoded.lines[match.range.first]
                addresses(decoded, match).mapNotNull(::remoteHost).map { Issue(line, Problem.REMOTE_FILE, it) }
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

    /**
     * Where a file address may follow: `url(`, `@import` (whose address is a string or a `url(`), or
     * `image-set(`, every string of which is an address.
     */
    private val FILE_REFERENCE = Regex("""url\(|@import|(?:-webkit-)?image-set\(""", RegexOption.IGNORE_CASE)

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
                    '\\' -> i = escapeEnd(text, i) - 1
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
     * [css] with comments blanked out (line breaks kept, so offsets and lines stay); comment markers inside strings,
     * escapes and unquoted `url(...)` addresses are text. [onUnclosed] gets the offset of a comment that is never
     * closed.
     */
    private fun blankComments(css: String, onUnclosed: (Int) -> Unit = {}): String {
        val out = StringBuilder(css)
        var i = 0
        while (i < css.length) {
            i = when {
                css[i] == '"' || css[i] == '\'' -> skipRawString(css, i) + 1
                css.startsWith("/*", i) -> {
                    val end = css.indexOf("*/", i + 2)
                    val stop = if (end < 0) css.length else end + 2
                    if (end < 0) onUnclosed(i)
                    for (j in i until stop) if (out[j] != '\n') out.setCharAt(j, ' ')
                    stop
                }
                css[i].isNameChar() || isEscape(css, i) -> nameEnd(css, i)
                else -> i + 1
            }
        }
        return out.toString()
    }

    /** Skips a string in the raw CSS (comment markers inside strings are text); problems are reported later. */
    private fun skipRawString(css: String, open: Int): Int {
        var i = open + 1
        while (i < css.length && css[i] != css[open] && css[i] != '\n') {
            i = if (css[i] == '\\') escapeEnd(css, i) else i + 1
        }
        return if (i < css.length && css[i] == '\n') i - 1 else i
    }

    /**
     * The index after the name at [start] (name characters and escapes, e.g. `\75rl`). For `url(` with an unquoted
     * address it is the index of the closing `)`: a browser reads comment markers and quotes there as part of the
     * address.
     */
    private fun nameEnd(css: String, start: Int): Int {
        // Only a name of three characters can be "url"; longer names are not kept whole.
        val name = StringBuilder()
        var i = start
        while (i < css.length && (css[i].isNameChar() || isEscape(css, i))) {
            if (css[i] == '\\') {
                val (code, end) = escapeAt(css, i)
                if (name.length <= URL_NAME.length) name.appendCodePoint(code)
                i = end
            } else {
                if (name.length <= URL_NAME.length) name.append(css[i])
                i++
            }
        }
        if (i == css.length || css[i] != '(' || !name.toString().equals(URL_NAME, ignoreCase = true)) return i
        var end = i + 1
        while (end < css.length && css[end] in CSS_WHITESPACE) end++
        if (end < css.length && (css[end] == '"' || css[end] == '\'')) return i
        while (end < css.length && css[end] != ')') end += if (css[end] == '\\') 2 else 1
        return minOf(end, css.length)
    }

    /** Letters, digits, `_`, `-` and non-ASCII characters. */
    private fun Char.isNameChar(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_' || this == '-' || code >= NON_ASCII

    /** A `\` at [i] that starts an escape: one not followed by a line break. */
    private fun isEscape(text: String, i: Int): Boolean =
        text[i] == '\\' && (i + 1 == text.length || text[i + 1] !in LINE_BREAKS)

    /**
     * The code point the escape at [start] (a `\`) stands for, and the index after it: up to six hex digits and one
     * whitespace after them (CRLF counts as one), or one character.
     */
    private fun escapeAt(text: String, start: Int): Pair<Int, Int> {
        if (start + 1 == text.length) return REPLACEMENT to text.length
        if (!text[start + 1].isHexDigit()) {
            val code = text.codePointAt(start + 1)
            return code to start + 1 + Character.charCount(code)
        }
        var end = start + 1
        while (end < text.length && end - start <= MAX_HEX_DIGITS && text[end].isHexDigit()) end++
        val code = text.substring(start + 1, end).toInt(HEX)
        end += when {
            text.startsWith("\r\n", end) -> 2
            end < text.length && text[end] in CSS_WHITESPACE -> 1
            else -> 0
        }
        return (if (code == 0 || code in SURROGATES || code > Character.MAX_CODE_POINT) REPLACEMENT else code) to end
    }

    /** The index after the escape at [start] (a `\`); a `\` before CRLF continues a string over one line break. */
    private fun escapeEnd(text: String, start: Int): Int =
        if (text.startsWith("\r\n", start + 1)) start + 3 else escapeAt(text, start).second

    /**
     * CSS with its escapes decoded.
     *
     * @param lines the 1-based line in the source of each character of [text].
     * @param escaped the character was written as an escape: an escaped quote, `)` or whitespace ends nothing.
     */
    private class Decoded(val text: String, val lines: IntArray, val escaped: BooleanArray)

    /** [text] (line breaks already `\n`) with CSS escapes replaced by their characters; an escaped line break is dropped. */
    private fun unescape(text: String): Decoded {
        val out = StringBuilder(text.length)
        val lines = IntArray(text.length)
        val escaped = BooleanArray(text.length)
        var line = 1
        var i = 0
        while (i < text.length) {
            val from = out.length
            val end = when {
                text[i] != '\\' || i + 1 == text.length -> {
                    out.append(text[i])
                    i + 1
                }
                text[i + 1] == '\n' -> i + 2
                else -> {
                    val (code, after) = escapeAt(text, i)
                    out.appendCodePoint(code)
                    after
                }
            }
            for (j in from until out.length) {
                lines[j] = line
                escaped[j] = end - i > 1
            }
            for (j in i until end) if (text[j] == '\n') line++
            i = end
        }
        return Decoded(out.toString(), lines, escaped)
    }

    /** The addresses the reference [match] points at: one for `url(` and `@import`, every string of an `image-set()`. */
    private fun addresses(decoded: Decoded, match: MatchResult): List<String> {
        val start = match.range.last + 1
        if (match.value.endsWith("image-set(", ignoreCase = true)) return imageSetValues(decoded, start)
        return listOfNotNull(urlValue(decoded, start, unquoted = match.value.startsWith("url", ignoreCase = true)))
    }

    /**
     * The address after `url(` or `@import` at [start] of [decoded] CSS: a quoted string, or with [unquoted] the text
     * up to `)`; null when none follows (an `@import url(...)`, whose `url(` is read on its own).
     */
    private fun urlValue(decoded: Decoded, start: Int, unquoted: Boolean): String? {
        val text = decoded.text
        fun isSpace(i: Int) = text[i] in CSS_WHITESPACE && !decoded.escaped[i]
        var i = start
        while (i < text.length && isSpace(i)) i++
        if (i == text.length) return null
        quotedValue(decoded, i)?.let { return it.first }
        if (!unquoted) return null
        val end = (i until text.length)
            .firstOrNull { (text[it] == ')' && !decoded.escaped[it]) || isSpace(it) } ?: text.length
        return text.substring(i, end)
    }

    /**
     * Every string up to the `)` that closes the `image-set()` opened before [start]: a browser takes a bare string
     * there as an address, as it takes a `url(...)`.
     */
    private fun imageSetValues(decoded: Decoded, start: Int): List<String> {
        val text = decoded.text
        val values = mutableListOf<String>()
        var i = start
        var depth = 1
        while (i < text.length && depth > 0) {
            val quoted = quotedValue(decoded, i)
            if (quoted != null) {
                values += quoted.first
                i = quoted.second
                continue
            }
            if (!decoded.escaped[i]) {
                if (text[i] == '(') depth++
                if (text[i] == ')') depth--
            }
            i++
        }
        return values
    }

    /** The string that a plain quote at [start] opens and the index after it, or null when no quote is there. */
    private fun quotedValue(decoded: Decoded, start: Int): Pair<String, Int>? {
        val text = decoded.text
        val quote = text[start]
        if (decoded.escaped[start] || (quote != '"' && quote != '\'')) return null
        val end = (start + 1 until text.length)
            .firstOrNull { (text[it] == quote || text[it] == '\n') && !decoded.escaped[it] } ?: text.length
        return text.substring(start + 1, end) to if (end < text.length && text[end] == quote) end + 1 else end
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
    private const val NON_ASCII = 0x80
    private const val URL_NAME = "url"
    private val SURROGATES = 0xD800..0xDFFF
    private val CSS_WHITESPACE = charArrayOf(' ', '\t', '\n', '\r', '\u000C')
    private val LINE_BREAKS = charArrayOf('\n', '\r', '\u000C')
}
