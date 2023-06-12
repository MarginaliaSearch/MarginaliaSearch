package nu.marginalia.summary.heuristic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicTextUtilTest {

    @Test
    void countOccurrencesOfAnyWord() {
        String sentence = "B A Baracus was an expert with the Abacus";
        assertEquals(4, HeuristicTextUtil.countOccurrencesOfAnyWord(sentence, Set.of("b", "a", "baracus", "abacus")));
    }

    @Test
    void containsWordInAnyCase() {
        String sentence = "B A Baracus was an expert with the Abacus";

        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "b"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "a"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "baracus"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "abacus"));
        assertFalse(HeuristicTextUtil.containsWordInAnyCase(sentence, "cus"));
    }

    @Test
    void containsWordAllLowerCase() {
        String sentence = "b a baracus was an expert with the abacus";

        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "b"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "a"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "baracus"));
        assertTrue(HeuristicTextUtil.containsWordInAnyCase(sentence, "abacus"));
        assertFalse(HeuristicTextUtil.containsWordInAnyCase(sentence, "cus"));
    }

}