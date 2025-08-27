package nu.marginalia.language.pos;

import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordSpan;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;

class PosPatternTest {


    @Test
    void matchSentence__singleTermPattern() {
        PosPattern pattern = new PosPattern(posTagger, "A1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern("A1", "A1", "A1");

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of(
                new WordSpan(0, 1),
                new WordSpan(1, 2),
                new WordSpan(2, 3)
        );

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }

    @Test
    void matchSentence__singleTermPattern_comma() {
        PosPattern pattern = new PosPattern(posTagger, "A1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern(
                new String[] {"A1", "A1", "A1"},
                new boolean[] { true, false, true}
        );

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of(
                new WordSpan(0, 1),
                new WordSpan(1, 2),
                new WordSpan(2, 3)
        );

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }

    @Test
    void matchSentence__threeTermPattern() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern(
                new String[] {"A1", "B1", "C1", "A1", "B1", "C1"},
                new boolean[] { false, false, true, false, false, true }
        );

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of(
                new WordSpan(0, 3),
                new WordSpan(3, 6)
        );

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }

    @Test
    void matchSentence__threeTermPattern_mismatch() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern(
                new String[] {"A1", "B1", "A1", "C1", "A1", "C1"},
                new boolean[] { false, false, true, false, false, true }
        );

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of();

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }

    @Test
    void matchSentence__threeTermPattern_overlap() {
        PosPattern pattern = new PosPattern(posTagger, "A1 A1 A1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern(
                new String[] {"A1", "A1", "A1", "A1"},
                new boolean[] { false, false, false, true }
        );

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of(
                new WordSpan(0, 3),
                new WordSpan(1, 4)
        );

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }

    @Test
    void matchSentence__threeTermPattern_comma() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");
        List<WordSpan> ret = new ArrayList<>();

        DocumentSentence sentence = createSentenceForPattern(
                new String[] {"A1", "B1", "C1", "A1", "B1", "C1", "A1", "B1", "C1"},
                new boolean[] { true, false, false, false, true, false, false, false, true }
        );

        int returnCount = pattern.matchSentence(sentence, ret);

        List<WordSpan> expected = List.of(
                new WordSpan(6, 9)
        );

        System.out.println(ret);
        System.out.println(expected);

        Assertions.assertEquals(expected, ret);
        Assertions.assertEquals(ret.size(), returnCount);
    }


    @Test
    void isMatch__singleTermPattern() {
        PosPattern pattern = new PosPattern(posTagger, "A1");

        DocumentSentence sentence = createSentenceForPattern("A1", "B1", "A1");

        Assertions.assertTrue(pattern.isMatch(sentence, 0));
        Assertions.assertFalse(pattern.isMatch(sentence, 1));
        Assertions.assertTrue(pattern.isMatch(sentence, 2));
    }

    @Test
    void isMatch__threeTermPattern() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");

        DocumentSentence sentence = createSentenceForPattern("A1", "B1", "A1", "B1", "C1");

        Assertions.assertFalse(pattern.isMatch(sentence, 0));
        Assertions.assertFalse(pattern.isMatch(sentence, 1));
        Assertions.assertTrue(pattern.isMatch(sentence, 2));
        Assertions.assertFalse(pattern.isMatch(sentence, 3));
        Assertions.assertFalse(pattern.isMatch(sentence, 4));
        Assertions.assertFalse(pattern.isMatch(sentence, 5));
    }

    @Test
    void isMatch__threeTermPattern_comma() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");

        DocumentSentence sentence = createSentenceForPattern(
                new String[] { "A1", "B1", "C1", "A1", "B1", "C1", "A1", "B1", "C1" },
                new boolean[] { true, false, false, false, true, false, false, false, true }
        );

        Assertions.assertFalse(pattern.isMatch(sentence, 0));
        Assertions.assertFalse(pattern.isMatch(sentence, 1));
        Assertions.assertFalse(pattern.isMatch(sentence, 2));
        Assertions.assertFalse(pattern.isMatch(sentence, 3));
        Assertions.assertFalse(pattern.isMatch(sentence, 4));
        Assertions.assertFalse(pattern.isMatch(sentence, 5));
        Assertions.assertTrue(pattern.isMatch(sentence, 6));
        Assertions.assertFalse(pattern.isMatch(sentence, 7));
        Assertions.assertFalse(pattern.isMatch(sentence, 8));
        Assertions.assertFalse(pattern.isMatch(sentence, 9));
    }



    @Test
    void matchTagPattern__singleTerm() {
        PosPattern pattern = new PosPattern(posTagger, "A1");
        PosPattern matchPattern = new PosPattern(posTagger, "A1 B1 A1");

        Assertions.assertEquals(bitSet(true, false, true), pattern.matchTagPattern(matchPattern.toArray()));
    }


    @Test
    void matchTagPattern__threeTerms() {
        PosPattern pattern = new PosPattern(posTagger, "A1 B1 C1");
        PosPattern matchPattern = new PosPattern(posTagger, "A1 B1 A1 B1 C1 A1 B1 C1");

        Assertions.assertEquals(bitSet(false, false, true, false, false, true, false, false), pattern.matchTagPattern(matchPattern.toArray()));
    }

    PosTagger posTagger = new PosTagger("en", List.of("A1", "B1", "C1", "A2", "B2", "C2"));

    DocumentSentence createSentenceForPattern(String... tags) {
        BitSet allSet = new BitSet(tags.length);
        allSet.set(0, tags.length);

        long[] encodedTags = new long[tags.length];
        for (int i = 0; i < tags.length; i++) {
            encodedTags[i] = posTagger.encodeTagName(tags[i]);
        }

        return new DocumentSentence(
                allSet,
                tags,
                encodedTags,
                tags,
                EnumSet.noneOf(HtmlTag.class),
                new BitSet(tags.length),
                new BitSet(tags.length),
                allSet
        );
    }
    DocumentSentence createSentenceForPattern(String[] tags, boolean[] commas) {
        BitSet allSet = new BitSet(tags.length);
        allSet.set(0, tags.length);

        BitSet commaSet = new BitSet(tags.length);
        for (int i = 0; i < commas.length; i++) {
            if (!commas[i]) commaSet.set(i);
        }

        long[] encodedTags = new long[tags.length];
        for (int i = 0; i < tags.length; i++) {
            encodedTags[i] = posTagger.encodeTagName(tags[i]);
        }

        return new DocumentSentence(
                commaSet,
                tags,
                encodedTags,
                tags,
                EnumSet.noneOf(HtmlTag.class),
                new BitSet(tags.length),
                new BitSet(tags.length),
                allSet
        );
    }


    BitSet bitSet(boolean... bits) {
        BitSet ret = new BitSet(bits.length);
        for (int i = 0; i < bits.length; i++) {
            if (bits[i])
                ret.set(i);
        }
        return ret;
    }

}