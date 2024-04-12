package nu.marginalia.segmentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NgramLexiconTest {
    NgramLexicon lexicon = new NgramLexicon();
    @BeforeEach
    public void setUp() {
        lexicon.clear();
    }

    void addNgram(String... ngram) {
        lexicon.incOrdered(HasherGroup.ordered().rollingHash(ngram));
    }

    @Test
    void findSegments() {
        addNgram("hello", "world");
        addNgram("rye", "bread");
        addNgram("rye", "world");

        List<String[]> segments = lexicon.findSegmentsStrings(2, 2, "hello", "world", "rye", "bread");

        assertEquals(2, segments.size());

        for (int i = 0; i < 2; i++) {
            var segment = segments.get(i);
            switch (i) {
                case 0 -> {
                    assertArrayEquals(new String[]{"hello", "world"}, segment);
                }
                case 1 -> {
                    assertArrayEquals(new String[]{"rye", "bread"}, segment);
                }
            }
        }

    }
}