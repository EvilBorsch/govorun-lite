package com.govorun.lite.transcriber

/**
 * Strips hesitation sounds and filler words ("слова-паразиты") from
 * recognised text before it reaches the input field.
 *
 * Two kinds of fillers:
 *  - HESITATION — sounds that are never meaningful in written text
 *    («э-э», «эмм», «хмм», «м-м», «а-а-а»). Removed anywhere, together
 *    with the punctuation GigaAM attaches to them («Хмм...», «э-э,»).
 *  - WORD — real words that are fillers only in a parenthetical position
 *    («ну», «типа», «как бы», «короче», «вот», «в общем»...). Each has a
 *    context guard so that «два типа файлов», «путь короче» or «Вот дом»
 *    survive untouched. When in doubt, the word is kept: a leftover filler
 *    is a cosmetic miss, a deleted real word changes meaning.
 *
 * After a removal the surrounding punctuation is repaired (no dangling
 * commas, no «, ?») and a sentence-initial filler hands its capital letter
 * to the next word: «Ну, это пример» → «Это пример».
 *
 * Pure Kotlin with no Android dependencies so it is covered by plain JVM
 * unit tests (see FillerFilterTest).
 */
object FillerFilter {

    // Same explicit class as Dictionary — see the note there on why not \p{L}.
    private const val W = "а-яёА-ЯЁa-zA-Z0-9"
    private const val BEFORE = "(?<![$W-])"
    private const val AFTER = "(?![$W]|-[$W])"

    private enum class Kind { HESITATION, WORD }

    /** Punctuation directly after the filler, normalised to its role. */
    private enum class RightPunct { NONE, COMMA, TERMINAL }

    private class Ctx(
        val sentenceStart: Boolean,
        val afterComma: Boolean,
        val right: RightPunct,
        /** Text following the filler and its punctuation, spaces skipped. */
        val rest: String,
    ) {
        val leftBound get() = sentenceStart || afterComma
    }

    private class Rule(
        pattern: String,
        val kind: Kind,
        val allowed: (Ctx) -> Boolean = { true },
    ) {
        val regex = Regex("$BEFORE(?:$pattern)$AFTER", RegexOption.IGNORE_CASE)
    }

    // «как бы» is a real conditional in «как бы ты поступил», «как бы то ни
    // было», «как бы не опоздать» — keep it when a pronoun or particle follows.
    private val KAK_BY_KEEP = Regex(
        "^(?:ты|вы|он|она|оно|они|мы|я|мне|нам|вам|тебе|ему|ей|им|это|то|ни|не|чего|бы)(?![$W])",
        RegexOption.IGNORE_CASE
    )

    private val RULES = listOf(
        // э, ээ, э-э-э, эм, эмм, э-м
        Rule("э+(?:-+э+)*(?:-?м+)?", Kind.HESITATION),
        // мм, ммм, м-м (a single «м» is a unit: «5 м»)
        Rule("м{2,}(?:-+м+)*|м(?:-+м+)+", Kind.HESITATION),
        // хм, хмм, хм-м
        Rule("х+м+(?:-+м+)*", Kind.HESITATION),
        // а-а, а-а-а, ааа (a single «а» is a conjunction)
        Rule("а(?:-+а+)+|а{2,}", Kind.HESITATION),
        Rule("ы+(?:-+ы+)*", Kind.HESITATION),
        Rule("uh+|uhm+|um+|erm+|hmm+", Kind.HESITATION),

        // Longer phrases first so «типа того» wins over «типа».
        Rule("типа того", Kind.WORD) { it.leftBound },
        Rule("короче говоря", Kind.WORD) { it.leftBound && it.right != RightPunct.NONE },
        Rule("собственно говоря", Kind.WORD) { it.leftBound && it.right != RightPunct.NONE },
        Rule("в общем(?:-то)?", Kind.WORD) { it.leftBound && it.right == RightPunct.COMMA },
        Rule("так сказать", Kind.WORD) { it.leftBound && it.right != RightPunct.NONE },
        Rule("это самое", Kind.WORD) { it.leftBound && it.right == RightPunct.COMMA },
        Rule("как[- ]бы", Kind.WORD) {
            !it.sentenceStart &&
                (it.afterComma && it.right == RightPunct.COMMA || !KAK_BY_KEEP.containsMatchIn(it.rest))
        },
        Rule("ну(?:-?у+)*", Kind.WORD) { it.leftBound },
        Rule("типа", Kind.WORD) { it.leftBound },
        Rule("короче", Kind.WORD) { it.leftBound && it.right != RightPunct.NONE },
        Rule("собственно", Kind.WORD) { it.leftBound && it.right == RightPunct.COMMA },
        Rule("вот", Kind.WORD) { it.leftBound && it.right != RightPunct.NONE },
    )

    private const val SENTENCE_END = ".!?…\n"
    private const val MAX_PASSES = 64

    fun apply(text: String): String {
        if (text.isBlank()) return text
        var current = text
        var passes = 0
        while (passes < MAX_PASSES) {
            current = removeFirst(current) ?: break
            passes++
        }
        return if (passes > 0) cleanup(current) else text
    }

    /** Removes the earliest removable filler; null when there is none. */
    private fun removeFirst(text: String): String? {
        var best: Pair<MatchResult, Rule>? = null
        for (rule in RULES) {
            for (m in rule.regex.findAll(text)) {
                if (best != null && m.range.first >= best.first.range.first) break
                if (isRemovable(text, m, rule)) {
                    best = m to rule
                    break
                }
            }
        }
        val (m, rule) = best ?: return null
        return remove(text, m, rule.kind)
    }

    private fun isRemovable(text: String, m: MatchResult, rule: Rule): Boolean {
        val ctx = context(text, m)
        if (rule.kind == Kind.WORD) {
            // «Ну?», «Вот!» — a filler word standing as a whole sentence
            // carries intonation; leave it alone.
            if (ctx.sentenceStart && ctx.right == RightPunct.TERMINAL) return false
        }
        return rule.allowed(ctx)
    }

    private fun context(text: String, m: MatchResult): Ctx {
        val start = m.range.first
        val end = m.range.last + 1
        val wsStart = skipSpacesBack(text, start)
        val prev = text.getOrNull(wsStart - 1)
        val punctEnd = punctEnd(text, end)
        val nextIdx = skipSpaces(text, punctEnd)
        return Ctx(
            sentenceStart = prev == null || prev in SENTENCE_END,
            afterComma = prev == ',' || prev == ';',
            right = rightPunct(text, end, punctEnd, nextIdx),
            rest = text.substring(nextIdx),
        )
    }

    private fun remove(text: String, m: MatchResult, kind: Kind): String {
        val start = m.range.first
        val end = m.range.last + 1
        val ctx = context(text, m)
        val wsStart = skipSpacesBack(text, start)
        val punctEnd = punctEnd(text, end)
        val nextIdx = skipSpaces(text, punctEnd)

        return when {
            ctx.sentenceStart -> {
                // Drop the filler, its punctuation and the spaces after it.
                // Keep the spaces before — they separate the previous sentence.
                val tail = text.substring(nextIdx)
                val capitalise = text[start].isUpperCase()
                text.substring(0, start) + (if (capitalise) tail.capitaliseFirst() else tail)
            }
            ctx.afterComma -> when (ctx.right) {
                // A parenthetical word owns both of its commas:
                // «Он, типа, пришёл» → «Он пришёл». A hesitation often sits
                // right after a real comma: «Привет, э-э, как» → «Привет, как».
                RightPunct.COMMA ->
                    if (kind == Kind.WORD) text.substring(0, wsStart - 1) + text.substring(punctEnd)
                    else text.substring(0, wsStart) + text.substring(punctEnd)
                // «Привет, э-э как» → «Привет, как»
                RightPunct.NONE -> text.substring(0, start) + text.substring(nextIdx)
                // «Как дела, э-э?» → «Как дела?» — the comma goes too.
                RightPunct.TERMINAL -> text.substring(0, wsStart - 1) + text.substring(end)
            }
            // Mid-sentence: «это как бы пример» → «это пример»,
            // «как дела э-э?» → «как дела?»
            wsStart < start -> text.substring(0, wsStart) + text.substring(end)
            else -> text.substring(0, start) + text.substring(nextIdx)
        }
    }

    private fun rightPunct(text: String, end: Int, punctEnd: Int, nextIdx: Int): RightPunct {
        if (punctEnd == end) return RightPunct.NONE
        val punct = text.substring(end, punctEnd)
        if (punct == "," || punct == ";") return RightPunct.COMMA
        val isEllipsis = punct == "…" || punct.all { it == '.' } && punct.length >= 2
        // «я, хмм... не знаю» — an ellipsis before a lowercase word is a
        // pause inside the sentence, not its end.
        if (isEllipsis && text.getOrNull(nextIdx)?.isLowerCase() == true) return RightPunct.COMMA
        return RightPunct.TERMINAL
    }

    /** End index of the punctuation run attached to a filler at [end]. */
    private fun punctEnd(text: String, end: Int): Int {
        val c = text.getOrNull(end) ?: return end
        if (c == ',' || c == ';') return end + 1
        var i = end
        while (i < text.length && text[i] in ".!?…") i++
        return i
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    private fun skipSpacesBack(text: String, from: Int): Int {
        var i = from
        while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
        return i
    }

    private fun String.capitaliseFirst(): String =
        if (isNotEmpty() && this[0].isLowerCase()) this[0].uppercaseChar() + substring(1) else this

    private val MULTI_SPACE = Regex("[ \\t]{2,}")
    private val SPACE_BEFORE_PUNCT = Regex("[ \\t]+([,.!?…;:])")
    private val DOUBLE_COMMA = Regex("([,;])[ \\t]*[,;]")
    private val COMMA_AFTER_END = Regex("([.!?…])[ \\t]*[,;]")
    private val LEADING_JUNK = Regex("^[\\s,;:]+")
    private val TRAILING_COMMA = Regex("[ \\t]*[,;:]+[ \\t]*$")

    private fun cleanup(text: String): String {
        var t = text
        t = MULTI_SPACE.replace(t, " ")
        t = SPACE_BEFORE_PUNCT.replace(t, "$1")
        t = DOUBLE_COMMA.replace(t, "$1")
        t = COMMA_AFTER_END.replace(t, "$1")
        t = LEADING_JUNK.replace(t, "")
        t = TRAILING_COMMA.replace(t, "")
        t = t.trim()
        return t.capitaliseFirstIf(text.trimStart().firstOrNull()?.isUpperCase() == true)
    }

    private fun String.capitaliseFirstIf(cond: Boolean) = if (cond) capitaliseFirst() else this
}
