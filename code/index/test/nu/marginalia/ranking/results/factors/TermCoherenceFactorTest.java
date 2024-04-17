package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.model.idx.WordMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TermCoherenceFactorTest {

    TermCoherenceFactor termCoherenceFactor = new TermCoherenceFactor();
    @Test
    public void testAllBitsSet() {
        var allPositionsSet = createSet(
                ~0L,
                ~0L
        );

        long mask = CompiledQueryAggregates.longBitmaskAggregate(
                allPositionsSet,
                SearchResultKeywordScore::positions
        );

        assertEquals(1.0, termCoherenceFactor.bitsSetFactor(mask), 0.01);

        assertEquals(1.0,
                termCoherenceFactor.calculateOverlap(
                        allPositionsSet.mapToLong(SearchResultKeywordScore::encodedWordMetadata)
                )
        );

    }

    @Test
    public void testNoBitsSet() {
        var allPositionsSet = createSet(
                0, 0
        );

        long mask = CompiledQueryAggregates.longBitmaskAggregate(allPositionsSet, score -> score.positions() & WordMetadata.POSITIONS_MASK);

        assertEquals(0, termCoherenceFactor.bitsSetFactor(mask), 0.01);

        assertEquals(0, termCoherenceFactor.calculateOverlap(allPositionsSet.mapToLong(SearchResultKeywordScore::encodedWordMetadata)));
    }

    @Test @SuppressWarnings("unchecked")
    public void testLowPosMatches() {
        var positions = createSet(
                List.of(0, 1, 2, 3), List.of(0, 1, 2, 3)
        );

        long mask = CompiledQueryAggregates.longBitmaskAggregate(positions, score -> score.positions() & WordMetadata.POSITIONS_MASK);
        printMask(mask);

    }

    @Test @SuppressWarnings("unchecked")
    public void testHiPosMatches() {
        var positions = createSet(
                List.of(55, 54, 53, 52), List.of(55, 54, 53, 52)
        );

        long mask = CompiledQueryAggregates.longBitmaskAggregate(positions, score -> score.positions() & WordMetadata.POSITIONS_MASK);
        printMask(mask);
    }

    @Test
    public void testBitMatchScaling() {
        for (int i = 1; i < 48; i++) {
            System.out.println(i + ":" + termCoherenceFactor.bitsSetFactor((1L << i) - 1));
        }
    }

    void printMask(long mask) {
        System.out.println(BrailleBlockPunchCards.printBits(mask, 48));
    }

    CompiledQuery<SearchResultKeywordScore> createSet(List<Integer>... maskPositions) {
        long[] positions = new long[maskPositions.length];

        for (int i = 0; i < maskPositions.length; i++) {
            for (long pos : maskPositions[i]) {
                positions[i] |= (1L<<pos);
            }
        }

        return createSet(positions);
    }

    CompiledQuery<SearchResultKeywordScore> createSet(long... positionMasks) {
        List<SearchResultKeywordScore> keywords = new ArrayList<>();

        for (int i = 0; i < positionMasks.length; i++) {
            keywords.add(new SearchResultKeywordScore("", 0,
                    new WordMetadata(positionMasks[i] & WordMetadata.POSITIONS_MASK, (byte) 0).encode()));
        }

        return CompiledQuery.just(keywords.toArray(SearchResultKeywordScore[]::new));
    }
}