package net.matsudamper.folderviewer.viewmodel.util

import java.util.regex.PatternSyntaxException

internal object TextSearchMatcher {
    const val MaxMatches: Int = 10_000

    fun find(
        text: String,
        query: String,
        useRegex: Boolean,
        matchCase: Boolean,
    ): Result {
        if (query.isEmpty()) return Result.Matches(listOf())
        return if (useRegex) {
            findRegex(text = text, query = query, matchCase = matchCase)
        } else {
            findLiteral(text = text, query = query, matchCase = matchCase)
        }
    }

    private fun findLiteral(
        text: String,
        query: String,
        matchCase: Boolean,
    ): Result {
        val ranges = buildList {
            var startIndex = 0
            while (startIndex < text.length && size < MaxMatches) {
                val found = text.indexOf(query, startIndex, ignoreCase = !matchCase)
                if (found < 0) break
                val endExclusive = found + query.length
                add(Range(start = found, endExclusive = endExclusive))
                startIndex = if (endExclusive > found) endExclusive else found + 1
            }
        }
        return Result.Matches(ranges)
    }

    private fun findRegex(
        text: String,
        query: String,
        matchCase: Boolean,
    ): Result {
        val options = buildSet {
            add(RegexOption.MULTILINE)
            if (!matchCase) add(RegexOption.IGNORE_CASE)
        }
        val regex = try {
            Regex(query, options)
        } catch (_: PatternSyntaxException) {
            return Result.InvalidPattern
        }
        val ranges = regex.findAll(text)
            .mapNotNull { result ->
                val endExclusive = result.range.last + 1
                if (result.range.first >= endExclusive) {
                    null
                } else {
                    Range(start = result.range.first, endExclusive = endExclusive)
                }
            }
            .take(MaxMatches)
            .toList()
        return Result.Matches(ranges)
    }

    data class Range(
        val start: Int,
        val endExclusive: Int,
    )

    sealed interface Result {
        data class Matches(val ranges: List<Range>) : Result

        data object InvalidPattern : Result
    }
}
