package nu.marginalia.search.valuation;

import nu.marginalia.index.client.model.results.EdgeSearchResultKeywordScore;
import nu.marginalia.language.statistics.TermFrequencyDict;
import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;

class SearchResultValuatorTest {

    TermFrequencyDict dict;
    SearchResultValuator valuator;

    @BeforeEach
    public void setUp() {

        dict = Mockito.mock(TermFrequencyDict.class);
        when(dict.docCount()).thenReturn(100_000);

        valuator = new SearchResultValuator(dict);

    }
    List<EdgeSearchResultKeywordScore> titleOnlyLowCountSet = List.of(
            new EdgeSearchResultKeywordScore(0, "bob",
                    wordMetadata(32, Set.of(1), EnumSet.of(EdgePageWordFlags.Title)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(EdgePageDocumentFlags.class)),
                    false)
    );
    List<EdgeSearchResultKeywordScore> highCountNoTitleSet = List.of(
            new EdgeSearchResultKeywordScore(0, "bob",
                    wordMetadata(129, Set.of(1,3,4,6,7,9,10,11,12,14,15,16), EnumSet.of(EdgePageWordFlags.TfIdfHigh)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(EdgePageDocumentFlags.class)),
                    false)
    );

    List<EdgeSearchResultKeywordScore> highCountSubjectSet = List.of(
            new EdgeSearchResultKeywordScore(0, "bob",
                    wordMetadata(129, Set.of(1,3,4,6,7,9,10,11,12,14,15,16), EnumSet.of(EdgePageWordFlags.TfIdfHigh, EdgePageWordFlags.Subjects)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(EdgePageDocumentFlags.class)),
                    false)
    );


    List<EdgeSearchResultKeywordScore> first = List.of(
            new EdgeSearchResultKeywordScore(0, "bob",
                    wordMetadata(202, Set.of(1,3,4,6,7,9,10,11), EnumSet.of(EdgePageWordFlags.TfIdfHigh)),
                    docMetadata(0, 2010, 0, 5, EnumSet.noneOf(EdgePageDocumentFlags.class)),
                    false)
    );

    @Test
    void evaluateTerms() {

        when(dict.getTermFreq("bob")).thenReturn(10L);

        double titleOnlyLowCount = valuator.evaluateTerms(titleOnlyLowCountSet, 10_000, 32);
        double titleLongOnlyLowCount = valuator.evaluateTerms(titleOnlyLowCountSet, 10_000, 72);
        double highCountNoTitle = valuator.evaluateTerms(highCountNoTitleSet, 10_000, 32);
        double highCountSubject = valuator.evaluateTerms(highCountSubjectSet, 10_000, 32);

        System.out.println(titleOnlyLowCount);
        System.out.println(titleLongOnlyLowCount);
        System.out.println(highCountNoTitle);
        System.out.println(highCountSubject);
    }

    private long docMetadata(int topology, int year, int sets, int quality, EnumSet<EdgePageDocumentFlags> flags) {
        return new DocumentMetadata(topology, PubDate.toYearByte(year), sets, quality, flags).encode();
    }

    private long wordMetadata(int tfIdf, Set<Integer> positions, Set<EdgePageWordFlags> wordFlags) {
        int posBits = positions.stream()
                .mapToInt(i -> (int)((1L << i) & 0xFFFF_FFFFL))
                .reduce((a,b) -> a|b)
                .orElse(0);

        return new WordMetadata(tfIdf, posBits, positions.size(), wordFlags).encode();
    }

}