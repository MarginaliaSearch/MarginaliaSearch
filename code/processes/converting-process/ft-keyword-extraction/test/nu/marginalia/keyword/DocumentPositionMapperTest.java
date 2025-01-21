package nu.marginalia.keyword;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentPositionMapperTest {
    private final DocumentPositionMapper positionMapper = new DocumentPositionMapper();
    static SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

    @Test
    public void testWordPattern() {
        Assertions.assertTrue(positionMapper.matchesWordPattern("test"));
        Assertions.assertTrue(positionMapper.matchesWordPattern("1234567890abcde"));
        Assertions.assertFalse(positionMapper.matchesWordPattern("1234567890abcdef"));

        Assertions.assertTrue(positionMapper.matchesWordPattern("test-test-test-test-test"));
        Assertions.assertFalse(positionMapper.matchesWordPattern("test-test-test-test-test-test-test-test-test"));
        Assertions.assertTrue(positionMapper.matchesWordPattern("192.168.1.100/24"));
        Assertions.assertTrue(positionMapper.matchesWordPattern("std::vector"));
        Assertions.assertTrue(positionMapper.matchesWordPattern("std::vector::push_back"));

        Assertions.assertTrue(positionMapper.matchesWordPattern("c++"));
        Assertions.assertTrue(positionMapper.matchesWordPattern("m*a*s*h"));
        Assertions.assertFalse(positionMapper.matchesWordPattern("Stulpnagelstrasse"));
    }

    @Test
    public void testBasic() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();
        DocumentLanguageData dld = new DocumentLanguageData(
                se.extractSentencesFromString("I am a teapot, short and stout", EnumSet.of(HtmlTag.CODE)),
                "I am a teapot"
        );

        int pos = positionMapper.mapDocumentPositions(keywordsBuilder, Mockito.mock(KeywordMetadata.class), dld);

        assertEquals(8, pos);
        assertEquals(IntList.of(1), keywordsBuilder.wordToPos.get("i"));
        assertEquals(IntList.of(2), keywordsBuilder.wordToPos.get("am"));
        assertEquals(IntList.of(3), keywordsBuilder.wordToPos.get("a"));
        assertEquals(IntList.of(4), keywordsBuilder.wordToPos.get("teapot"));
        assertEquals(IntList.of(5), keywordsBuilder.wordToPos.get("short"));
        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("and"));
        assertEquals(IntList.of(7), keywordsBuilder.wordToPos.get("stout"));

        var codeSpans = keywordsBuilder.wordSpans.get(HtmlTag.CODE);
        assertEquals(1, codeSpans.size());
        var codeSpan = codeSpans.getFirst();

        assertEquals(1, codeSpan.start());
        assertEquals(8, codeSpan.end());
    }


    @Test
    public void testLinksSingleWord1Rep() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString("Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 1 });

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("zelda"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(1, linkTextSpans.size());
        var codeSpan = linkTextSpans.getFirst();

        assertEquals(6, codeSpan.start());
        assertEquals(7, codeSpan.end());
    }

    @Test
    public void testLinksSingleWord2Reps() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString("Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 4 }); // This will become 2 repetitions, formula is ~ sqrt(counts)

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6, 9), keywordsBuilder.wordToPos.get("zelda"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentKeywordsBuilder.DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(7, span.end());

        span = linkTextSpans.get(1);

        assertEquals(9, span.start());
        assertEquals(10, span.end());
    }

    @Test
    public void testLinksTwoWords2Reps() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences = se.extractSentencesFromString("Zelda II", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences.size());
        TIntList counts = new TIntArrayList(new int[] { 4 });

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentences, counts));

        assertEquals(IntList.of(6, 10), keywordsBuilder.wordToPos.get("zelda"));
        assertEquals(IntList.of(7, 11), keywordsBuilder.wordToPos.get("ii"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentKeywordsBuilder.DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(8, span.end());

        span = linkTextSpans.get(1);

        assertEquals(10, span.start());
        assertEquals(12, span.end());
    }


    @Test
    public void testLinksTwoSent1Word1Rep() {
        DocumentKeywordsBuilder keywordsBuilder = new DocumentKeywordsBuilder();

        var sentences1 = se.extractSentencesFromString("Zelda", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        var sentences2 = se.extractSentencesFromString("Link", EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
        assertEquals(1, sentences1.size());
        assertEquals(1, sentences2.size());
        TIntList counts = new TIntArrayList(new int[] { 1, 1 });

        List<DocumentSentence> sentencesAll = new ArrayList<>();
        sentencesAll.addAll(sentences1);
        sentencesAll.addAll(sentences2);

        positionMapper.mapLinkTextPositions(5, keywordsBuilder, Mockito.mock(KeywordMetadata.class),
                new LinkTexts(sentencesAll, counts));

        assertEquals(IntList.of(6), keywordsBuilder.wordToPos.get("zelda"));
        assertEquals(IntList.of(9), keywordsBuilder.wordToPos.get("link"));

        var linkTextSpans = keywordsBuilder.wordSpans.get(HtmlTag.EXTERNAL_LINKTEXT);
        assertEquals(2, linkTextSpans.size());

        DocumentKeywordsBuilder.DocumentWordSpan span;
        span = linkTextSpans.get(0);

        assertEquals(6, span.start());
        assertEquals(7, span.end());

        span = linkTextSpans.get(1);

        assertEquals(9, span.start());
        assertEquals(10, span.end());
    }


}