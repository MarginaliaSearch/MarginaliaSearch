package nu.marginalia.index.model;

import nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SearchContextTest {

    @Test
    void harmonizeVariantFrequencies__variantsShareTheLargestFrequency() {
        var query = CompiledQueryParser.parse("( napoleon | napoleons )");

        int[] frequencies = new int[query.size()];
        frequencies[query.at(0).equals("napoleon") ? 0 : 1] = 1000;
        frequencies[query.at(0).equals("napoleon") ? 1 : 0] = 3;

        SearchContext.harmonizeVariantFrequencies(query.root(), frequencies);

        assertArrayEquals(new int[] { 1000, 1000 }, frequencies);
    }

    @Test
    void harmonizeVariantFrequencies__phraseAlternativesAreLeftAlone() {
        var query = CompiledQueryParser.parse("( world_war_2 | ( world war 2 ) )");

        int[] frequencies = new int[query.size()];
        for (int i = 0; i < query.size(); i++) {
            frequencies[i] = 100 + i;
        }
        int[] expected = frequencies.clone();

        SearchContext.harmonizeVariantFrequencies(query.root(), frequencies);

        assertArrayEquals(expected, frequencies);
    }
}
