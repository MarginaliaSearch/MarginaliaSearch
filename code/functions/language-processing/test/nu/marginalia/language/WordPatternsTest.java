package nu.marginalia.language;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordPatternsTest {

    @Test
    public void testIsDiscardedByTokenizer() {
        assertTrue(WordPatterns.isDiscardedByTokenizer(""));
        assertTrue(WordPatterns.isDiscardedByTokenizer(" "));
        assertTrue(WordPatterns.isDiscardedByTokenizer("-"));
        assertTrue(WordPatterns.isDiscardedByTokenizer("--"));
        assertTrue(WordPatterns.isDiscardedByTokenizer("*"));
        assertTrue(WordPatterns.isDiscardedByTokenizer("/"));
        assertTrue(WordPatterns.isDiscardedByTokenizer("*/"));
        assertTrue(WordPatterns.isDiscardedByTokenizer("a".repeat(WordPatterns.MAX_WORD_LENGTH)));

        assertFalse(WordPatterns.isDiscardedByTokenizer("a"));
        assertFalse(WordPatterns.isDiscardedByTokenizer("foo"));
        assertFalse(WordPatterns.isDiscardedByTokenizer("foo-"));
        assertFalse(WordPatterns.isDiscardedByTokenizer("-foo"));
        assertFalse(WordPatterns.isDiscardedByTokenizer("123456789012345678"));
        assertFalse(WordPatterns.isDiscardedByTokenizer("a".repeat(WordPatterns.MAX_WORD_LENGTH - 1)));
    }

    @Test
    public void testPhraseConstraints() {
        assertTrue(WordPatterns.isDiscardedByTokenizer("-"));
        assertTrue(WordPatterns.isStopWord("-"));

        assertTrue(WordPatterns.isDiscardedByTokenizer("*"));
        assertFalse(WordPatterns.isStopWord("*"));

        assertFalse(WordPatterns.isDiscardedByTokenizer("123456789012345678"));
        assertTrue(WordPatterns.isStopWord("123456789012345678"));

        assertFalse(WordPatterns.isDiscardedByTokenizer("foo"));
        assertFalse(WordPatterns.isStopWord("foo"));
    }
}
