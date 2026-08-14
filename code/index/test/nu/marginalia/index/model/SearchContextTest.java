package nu.marginalia.index.model;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
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

        SearchContext.harmonizeVariantTermFrequencies(query.root(), frequencies);

        assertArrayEquals(new int[] { 1000, 1000 }, frequencies);
    }

    @Test
    void variantClasses__variantsShareARepresentative() {
        var query = CompiledQueryParser.parse("( elden ( ring | rings ) | elden_ring )");

        int[] classes = SearchContext.variantClasses(query.root(), query.size());

        int ring = indexOf(query, "ring");
        int rings = indexOf(query, "rings");
        int elden = indexOf(query, "elden");
        int ngram = indexOf(query, "elden_ring");

        assertArrayEquals(new int[] { classes[ring] }, new int[] { classes[rings] });
        assertArrayEquals(new int[] { elden }, new int[] { classes[elden] });
        assertArrayEquals(new int[] { ngram }, new int[] { classes[ngram] });
    }

    private static int indexOf(CompiledQuery<String> query, String term) {
        for (int i = 0; i < query.size(); i++) {
            if (query.at(i).equals(term))
                return i;
        }
        throw new IllegalArgumentException(term);
    }

    @Test
    void harmonizeVariantFrequencies__phraseAlternativesAreLeftAlone() {
        var query = CompiledQueryParser.parse("( world_war_2 | ( world war 2 ) )");

        int[] frequencies = new int[query.size()];
        for (int i = 0; i < query.size(); i++) {
            frequencies[i] = 100 + i;
        }
        int[] expected = frequencies.clone();

        SearchContext.harmonizeVariantTermFrequencies(query.root(), frequencies);

        assertArrayEquals(expected, frequencies);
    }
}
