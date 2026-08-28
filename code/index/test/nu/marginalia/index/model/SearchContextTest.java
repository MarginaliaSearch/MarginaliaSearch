package nu.marginalia.index.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SearchContextTest {

    @Test
    void harmonizeVariantFrequencies() {
        int[] frequencies = { 1000, 3 };

        SearchContext.harmonizeVariantTermFrequencies(new int[] { 0, 0 }, frequencies);

        assertArrayEquals(new int[] { 1000, 1000 }, frequencies);
    }

    @Test
    void harmonizeVariantFrequencies__unrelatedTerms() {
        int[] frequencies = { 100, 101, 102, 103 };

        SearchContext.harmonizeVariantTermFrequencies(new int[] { 0, 1, 2, 3 }, frequencies);

        assertArrayEquals(new int[] { 100, 101, 102, 103 }, frequencies);
    }

    @Test
    void harmonizeVariantFrequencies__independent() {
        int[] frequencies = { 10, 50, 7, 300 };

        SearchContext.harmonizeVariantTermFrequencies(new int[] { 0, 0, 2, 2 }, frequencies);

        assertArrayEquals(new int[] { 50, 50, 300, 300 }, frequencies);
    }
}
