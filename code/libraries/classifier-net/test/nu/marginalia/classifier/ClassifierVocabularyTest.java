package nu.marginalia.classifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassifierVocabularyTest {
    @Test
    public void testUnigrams() {
        var vocab = new ClassifierVocabulary(
                List.of("foo", "bar")
        );

        Assertions.assertArrayEquals(new int[] { 0 }, vocab.features("foo"));
        Assertions.assertArrayEquals(new int[] { 0 }, vocab.features("foo foo"));
        Assertions.assertArrayEquals(new int[] { 1 }, vocab.features("bar"));
        Assertions.assertArrayEquals(new int[] { 0, 1 }, vocab.features("foo bar"));
        Assertions.assertArrayEquals(new int[] { 1, 0 }, vocab.features("bar foo"));
        Assertions.assertArrayEquals(new int[] { 1, 0 }, vocab.features("bar baz foo"));
    }

    @Test
    public void testBigrams() {
        var vocab = new ClassifierVocabulary(
                List.of("foo", "bar", "foo_bar")
        );

        Assertions.assertArrayEquals(new int[] { 0 }, vocab.features("foo"));
        Assertions.assertArrayEquals(new int[] { 0 }, vocab.features("foo foo"));
        Assertions.assertArrayEquals(new int[] { 1 }, vocab.features("bar"));
        Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, vocab.features("foo bar"));
        Assertions.assertArrayEquals(new int[] { 1, 0 }, vocab.features("bar foo"));
        Assertions.assertArrayEquals(new int[] { 1, 0 }, vocab.features("bar baz foo"));
        Assertions.assertArrayEquals(new int[] { 0, 1 }, vocab.features("foo baz bar"));
    }
}