package nu.marginalia.ranking;

import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.ranking.factors.*;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.mockito.Mockito.when;

class ResultValuatorTest {

    TermFrequencyDict dict;
    ResultValuator valuator;

    @BeforeEach
    public void setUp() {

        dict = Mockito.mock(TermFrequencyDict.class);
        when(dict.docCount()).thenReturn(100_000);

        valuator = new ResultValuator(
                new Bm25Factor(),
                new TermCoherenceFactor(),
                new PriorityTermBonus()
        );

    }
    List<SearchResultKeywordScore> titleOnlyLowCountSet = List.of(
            new SearchResultKeywordScore(0, "bob",
                    wordMetadata(Set.of(1), EnumSet.of(WordFlags.Title)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(DocumentFlags.class)),
                    false)
    );
    List<SearchResultKeywordScore> highCountNoTitleSet = List.of(
            new SearchResultKeywordScore(0, "bob",
                    wordMetadata(Set.of(1,3,4,6,7,9,10,11,12,14,15,16), EnumSet.of(WordFlags.TfIdfHigh)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(DocumentFlags.class)),
                    false)
    );

    List<SearchResultKeywordScore> highCountSubjectSet = List.of(
            new SearchResultKeywordScore(0, "bob",
                    wordMetadata(Set.of(1,3,4,6,7,9,10,11,12,14,15,16), EnumSet.of(WordFlags.TfIdfHigh, WordFlags.Subjects)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(DocumentFlags.class)),
                    false)
    );


    @Test
    void evaluateTerms() {

        when(dict.getTermFreq("bob")).thenReturn(10);
        ResultRankingContext context = new ResultRankingContext(100000,
                ResultRankingParameters.sensibleDefaults(),
                Map.of("bob", 10), Collections.emptyMap());

        double titleOnlyLowCount = valuator.calculateSearchResultValue(titleOnlyLowCountSet, 10_000, context);
        double titleLongOnlyLowCount = valuator.calculateSearchResultValue(titleOnlyLowCountSet, 10_000, context);
        double highCountNoTitle = valuator.calculateSearchResultValue(highCountNoTitleSet, 10_000, context);
        double highCountSubject = valuator.calculateSearchResultValue(highCountSubjectSet, 10_000, context);

        System.out.println(titleOnlyLowCount);
        System.out.println(titleLongOnlyLowCount);
        System.out.println(highCountNoTitle);
        System.out.println(highCountSubject);
    }

    private long docMetadata(int topology, int year, int sets, int quality, EnumSet<DocumentFlags> flags) {
        return new DocumentMetadata(topology, PubDate.toYearByte(year), sets, quality, flags).encode();
    }

    private long wordMetadata(Set<Integer> positions, Set<WordFlags> wordFlags) {
        long posBits = positions.stream()
                .mapToLong(i -> ((1L << i) & 0xFF_FFFF_FFFF_FFFFL))
                .reduce((a,b) -> a|b)
                .orElse(0L);

        return new WordMetadata(posBits, wordFlags).encode();
    }

}