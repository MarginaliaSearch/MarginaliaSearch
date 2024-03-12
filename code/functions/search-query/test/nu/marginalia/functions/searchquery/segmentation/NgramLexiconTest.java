package nu.marginalia.functions.searchquery.segmentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NgramLexiconTest {
    NgramLexicon lexicon = new NgramLexicon();
    @BeforeEach
    public void setUp() {
        lexicon.clear();
    }

    void addNgram(String... ngram) {
        lexicon.incOrdered(HasherGroup.ordered().rollingHash(ngram));
        lexicon.addUnordered(HasherGroup.unordered().rollingHash(ngram));
    }

    @Test
    void findSegments() {
        addNgram("hello", "world");
        addNgram("rye", "bread");
        addNgram("rye", "world");

        String[] sent = { "hello", "world", "rye", "bread" };
        var segments = lexicon.findSegments(2, "hello", "world", "rye", "bread");

        assertEquals(3, segments.size());

        for (int i = 0; i < 3; i++) {
            var segment = segments.get(i);
            switch (i) {
                case 0 -> {
                    assertArrayEquals(new String[]{"hello", "world"}, segment.project(sent));
                    assertEquals(1, segment.count());
                    assertEquals(NgramLexicon.PositionType.NGRAM, segment.type());
                }
                case 1 -> {
                    assertArrayEquals(new String[]{"world", "rye"}, segment.project(sent));
                    assertEquals(0, segment.count());
                    assertEquals(NgramLexicon.PositionType.PERMUTATION, segment.type());
                }
                case 2 -> {
                    assertArrayEquals(new String[]{"rye", "bread"}, segment.project(sent));
                    assertEquals(1, segment.count());
                    assertEquals(NgramLexicon.PositionType.NGRAM, segment.type());
                }
            }
        }

    }
}