package net.matsudamper.folderviewer.viewmodel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TextSearchMatcherTest {
    @Test
    fun find_returnsEmptyWhenQueryIsEmpty() {
        val result = TextSearchMatcher.find(
            text = "abc",
            query = "",
            useRegex = false,
            matchCase = false,
        )
        assertEquals(TextSearchMatcher.Result.Matches(listOf()), result)
    }

    @Test
    fun find_matchesLiteralIgnoringCase() {
        val result = TextSearchMatcher.find(
            text = "Foo foo FOO",
            query = "foo",
            useRegex = false,
            matchCase = false,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(
                    TextSearchMatcher.Range(start = 0, endExclusive = 3),
                    TextSearchMatcher.Range(start = 4, endExclusive = 7),
                    TextSearchMatcher.Range(start = 8, endExclusive = 11),
                ),
            ),
            result,
        )
    }

    @Test
    fun find_matchesLiteralWithCase() {
        val result = TextSearchMatcher.find(
            text = "Foo foo",
            query = "foo",
            useRegex = false,
            matchCase = true,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(TextSearchMatcher.Range(start = 4, endExclusive = 7)),
            ),
            result,
        )
    }

    @Test
    fun find_matchesJapaneseLiteral() {
        val result = TextSearchMatcher.find(
            text = "あいう",
            query = "いう",
            useRegex = false,
            matchCase = false,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(TextSearchMatcher.Range(start = 1, endExclusive = 3)),
            ),
            result,
        )
    }

    @Test
    fun find_advancesPastLiteralMatch() {
        val result = TextSearchMatcher.find(
            text = "aaa",
            query = "aa",
            useRegex = false,
            matchCase = true,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(TextSearchMatcher.Range(start = 0, endExclusive = 2)),
            ),
            result,
        )
    }

    @Test
    fun find_matchesRegexPerLine() {
        val text = "fun main()\nfun greet()"
        val result = TextSearchMatcher.find(
            text = text,
            query = "^fun",
            useRegex = true,
            matchCase = true,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(
                    TextSearchMatcher.Range(start = 0, endExclusive = 3),
                    TextSearchMatcher.Range(start = 11, endExclusive = 14),
                ),
            ),
            result,
        )
    }

    @Test
    fun find_regexDotDoesNotCrossLine() {
        val result = TextSearchMatcher.find(
            text = "a\nb",
            query = "a.b",
            useRegex = true,
            matchCase = true,
        )
        assertEquals(TextSearchMatcher.Result.Matches(listOf()), result)
    }

    @Test
    fun find_regexIgnoresCaseWhenMatchCaseIsOff() {
        val result = TextSearchMatcher.find(
            text = "foo",
            query = "FOO",
            useRegex = true,
            matchCase = false,
        )
        assertEquals(
            TextSearchMatcher.Result.Matches(
                listOf(TextSearchMatcher.Range(start = 0, endExclusive = 3)),
            ),
            result,
        )
    }

    @Test
    fun find_returnsInvalidPattern() {
        val result = TextSearchMatcher.find(
            text = "abc",
            query = "[",
            useRegex = true,
            matchCase = false,
        )
        assertEquals(TextSearchMatcher.Result.InvalidPattern, result)
    }

    @Test
    fun find_skipsZeroLengthRegexMatches() {
        val result = TextSearchMatcher.find(
            text = "ab",
            query = "a*",
            useRegex = true,
            matchCase = true,
        )
        val matches = result as TextSearchMatcher.Result.Matches
        assertTrue(matches.ranges.all { range -> range.endExclusive > range.start })
    }
}
