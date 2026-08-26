package nu.marginalia.index.results;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.results.snippet.SentenceSnippetExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SentenceSnippetExtractorTest {

    @Test
    void test__bestSentence() {
        String text = "The first sentence is not interesting\nthe second sentence mentions potato flour manufacture\nthe third does not\n";

        // "potato" is token 12, "flour" is token 13
        IntList[] positions = new IntList[] {
                IntList.of(12),
                IntList.of(13),
        };

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(positions, weights(2), classes(2));

        assertNotNull(snippet);
        assertTrue(snippet.contains("potato flour"), snippet);
        assertFalse(snippet.contains("\n"));
    }

    @Test
    void test__noMatches_fallbackExcerpt() {
        assertEquals("some text", new SentenceSnippetExtractor("some text\n", Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of() }, weights(1), classes(1)));
        assertEquals("some text", new SentenceSnippetExtractor("some text\n", Integer.MAX_VALUE, null).extract(new IntList[] { null }, weights(1), classes(1)));

        assertEquals("one two three", new SentenceSnippetExtractor("one two three\n", Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of(4, 100) }, weights(1), classes(1)));

        assertNull(new SentenceSnippetExtractor("", Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of() }, weights(1), classes(1)));
    }

    @Test
    void testEmptySentence() {
        String leading = new SentenceSnippetExtractor("\nhello world\n", Integer.MAX_VALUE, null)
                .extract(new IntList[] { IntList.of(1) }, weights(1), classes(1));
        assertEquals("hello world", leading);

        // "beta" is token 2 right after "alpha", despite the empty sentence between them
        String middle = new SentenceSnippetExtractor("alpha\n\nbeta\n", Integer.MAX_VALUE, null)
                .extract(new IntList[] { IntList.of(2) }, weights(1), classes(1));
        assertNotNull(middle);
        assertTrue(middle.contains("beta"), middle);

        assertNull(new SentenceSnippetExtractor("\n\n", Integer.MAX_VALUE, null)
                .extract(new IntList[] { IntList.of() }, weights(1), classes(1)));
    }

    @Test
    void testSnippetStart() {
        // The match sits at the very end of its sentence, the fragment must still start at the sentence boundary
        String text = "An unrelated opening sentence sits here\nThis quite long sentence rambles onward for a spell and finally arrives at the needle\nAnd a following sentence provides context\n";

        // "needle" is the last token of the second sentence, token 21
        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of(21) }, weights(1), classes(1));

        assertNotNull(snippet);
        System.out.println(snippet);
        assertTrue(snippet.startsWith("This quite long sentence"), snippet);
        assertTrue(snippet.contains("needle"), snippet);
    }

    @Test
    void testExtension() {
        String text = "The needle is here\nA short follow-up comes after\n";

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of(2) }, weights(1), classes(1));

        assertNotNull(snippet);
        assertTrue(snippet.contains("The needle is here. A short follow-up comes after"), snippet);
    }

    @Test
    void testPartialExtension() {
        String paragraph = "lorem ipsum dolor sit amet ".repeat(20).trim();
        String text = "Interesting heading\n" + paragraph + "\n";

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null)
                .extract(new IntList[] { IntList.of(2) }, weights(1), classes(1));

        assertNotNull(snippet);
        System.out.println(snippet);
        assertTrue(snippet.startsWith("Interesting heading. lorem ipsum"), snippet);
        assertTrue(snippet.length() > 200, snippet);
        assertTrue(snippet.endsWith("..."), snippet);
    }

    @Test
    void testWeights() {
        // "common" appears in the first sentence, the rare term in the second,
        // with a heavily weighted rare term the second sentence must win
        String text = "The common word appears here\nThe rareword appears in this other sentence\n";

        IntList[] positions = new IntList[] {
                IntList.of(2),   // common
                IntList.of(7),   // rareword
        };

        String weighted = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(positions, new float[] { 0.1f, 10f }, classes(2));

        assertNotNull(weighted);
        assertTrue(weighted.contains("rareword"), weighted);
    }

    @Test
    void testMissingTerm() {
        // Terms A and B never co-occur, the snippet should contain two fragments covering both
        StringBuilder text = new StringBuilder();
        text.append("The alpha term is in this first sentence\n");
        for (int i = 0; i < 40; i++) {
            text.append("filler sentence number ").append(i).append(" with content\n");
        }
        text.append("The beta term is in this last sentence\n");

        int alphaOrdinal = 2;
        int betaOrdinal = 2 + 8 + 40 * 6;

        String snippet = new SentenceSnippetExtractor(text.toString(), Integer.MAX_VALUE, null)
                .extract(new IntList[] { IntList.of(alphaOrdinal), IntList.of(betaOrdinal) }, weights(2), classes(2));

        System.out.println(snippet);

        assertNotNull(snippet);
        assertTrue(snippet.contains("alpha"), snippet);
        assertTrue(snippet.contains("beta"), snippet);
        assertTrue(snippet.contains(" // "), snippet);
    }

    @Test
    void testExceptSkipsTitle() {
        String text = "Plato\nPlato was an ancient Greek philosopher of Classical Athens\nHe influenced nearly all of western philosophy\n";

        String lead = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, IntList.of(1, 2)).extractDocumentBeginning();

        assertNotNull(lead);
        assertTrue(lead.startsWith("Plato was an ancient Greek philosopher"), lead);
        assertTrue(lead.contains("Athens. He influenced"), lead);
    }


    @Test
    void testVariant() {
        // Models the "elden ring" / "lord of the rings" scenario, where the best sentence
        // covers both canonical terms, and a spelling variant of one of them
        // occurring elsewhere must not read as an uncovered term and pull in a
        // second fragment
        String text = "He credited the novels The Lord of the Rings as inspiration\nElden Ring was revealed at E3 2019 in June\n";

        IntList[] positions = new IntList[] {
                IntList.of(12),      // elden
                IntList.of(13),      // ring
                IntList.of(9),       // rings (variant of ring)
        };
        int[] classes = new int[] { 0, 1, 1 };

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(positions, weights(3), classes);

        assertNotNull(snippet);
        assertFalse(snippet.contains("Lord of the Rings"), snippet);
        assertTrue(snippet.contains("Elden Ring was revealed"), snippet);

        String unclassed = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(positions, weights(3), classes(3));
        assertNotNull(unclassed);
        assertTrue(unclassed.contains("Lord of the Rings"), unclassed);
    }

    @Test
    void testExcludedTitleRangeAvoided() {
        String text = "Plato\nPlato was an ancient Greek philosopher of Classical Athens\n";

        // "Plato" occurs at ordinals 1 (title) and 2 (body); title span is [1, 2)
        IntList[] positions = new IntList[] { IntList.of(1, 2) };

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, IntList.of(1, 2)).extract(positions, weights(1), classes(1));

        assertNotNull(snippet);
        assertTrue(snippet.startsWith("Plato was an ancient"), snippet);

        assertEquals("Plato was an ancient Greek philosopher of Classical Athens",
                new SentenceSnippetExtractor(text, Integer.MAX_VALUE, IntList.of(1, 2)).extract(new IntList[] { IntList.of(1) }, weights(1), classes(1)));
    }

    @Test
    void testBudget() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            if (i == 5) text.append("needle ");
            else text.append("longwordnumber").append(i).append(' ');
        }
        text.append('\n');

        String snippet = new SentenceSnippetExtractor(text.toString(), Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of(5) }, weights(1), classes(1));

        assertNotNull(snippet);
        assertTrue(snippet.contains("needle"), snippet);
        assertTrue(snippet.length() < 300, "snippet too long: " + snippet.length());
        assertTrue(snippet.endsWith("..."), snippet);
    }

    @Test
    void testLongSentence() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            if (i == 80) text.append("needle ");
            else text.append("longwordnumber").append(i).append(' ');
        }
        text.append('\n');

        String snippet = new SentenceSnippetExtractor(text.toString(), Integer.MAX_VALUE, null).extract(new IntList[] { IntList.of(80) }, weights(1), classes(1));

        assertNotNull(snippet);
        assertTrue(snippet.startsWith("...needle"), snippet);
    }

    @Test
    void testScatteredTerms() {
        String text = "The elden word and later the ring word appear scattered around this sentence here\nThis mentions Elden Ring together\n";

        IntList[] positions = new IntList[] {
                IntList.of(2, 16),  // elden: scattered, then adjacent pair
                IntList.of(7, 17),  // ring
        };

        String snippet = new SentenceSnippetExtractor(text, Integer.MAX_VALUE, null).extract(positions, weights(2), classes(2));

        assertNotNull(snippet);
        assertTrue(snippet.startsWith("This mentions Elden Ring"), snippet);
    }

    float[] weights(int n) {
        float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            weights[i] = 1f;
        }
        return weights;
    }

    int[] classes(int n) {
        int[] classes = new int[n];
        for (int i = 0; i < n; i++) {
            classes[i] = i;
        }
        return classes;
    }
}
