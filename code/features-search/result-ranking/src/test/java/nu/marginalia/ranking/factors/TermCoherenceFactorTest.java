package nu.marginalia.ranking.factors;

import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.ranking.ResultKeywordSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TermCoherenceFactorTest {

    TermCoherenceFactor termCoherenceFactor = new TermCoherenceFactor();
    @Test
    public void testAllBitsSet() {
        var allPositionsSet = createSet(
                ~0, ~0
        );

        int mask = termCoherenceFactor.combinedMask(allPositionsSet);

        assertEquals(1.0, termCoherenceFactor.bitPositionFactor(mask), 0.01);
        assertEquals(1.0, termCoherenceFactor.bitsSetFactor(mask), 0.01);

        assertEquals(1.0, termCoherenceFactor.calculate(allPositionsSet));
    }

    @Test
    public void testNoBitsSet() {
        var allPositionsSet = createSet(
                0, 0
        );

        int mask = termCoherenceFactor.combinedMask(allPositionsSet);

        assertEquals(0, termCoherenceFactor.bitPositionFactor(mask), 0.01);
        assertEquals(0, termCoherenceFactor.bitsSetFactor(mask), 0.01);

        assertEquals(0, termCoherenceFactor.calculate(allPositionsSet));
    }

    @Test @SuppressWarnings("unchecked")
    public void testLowPosMatches() {
        var allPositionsSet = createSet(
                List.of(0, 1, 2, 3), List.of(0, 1, 2, 3)
        );

        int mask = termCoherenceFactor.combinedMask(allPositionsSet);
        printMask(mask);

        assertEquals(1.0, termCoherenceFactor.bitPositionFactor(mask), 0.01);
    }

    @Test @SuppressWarnings("unchecked")
    public void testHiPosMatches() {
        var allPositionsSet = createSet(
                List.of(28, 29, 30, 31), List.of(28, 29, 30, 31)
        );

        int mask = termCoherenceFactor.combinedMask(allPositionsSet);
        printMask(mask);
        assertEquals(0.125, termCoherenceFactor.bitPositionFactor(mask), 0.01);
    }

    @Test
    public void testBitMatchScaling() {
        for (int i = 1; i < 32; i++) {
            System.out.println(i + ":" + termCoherenceFactor.bitsSetFactor((1 << i) - 1));
        }
    }

    void printMask(int mask) {
        System.out.println(BrailleBlockPunchCards.printBits(mask, 32));
    }

    ResultKeywordSet createSet(List<Integer>... maskPositions) {
        int[] positions = new int[maskPositions.length];

        for (int i = 0; i < maskPositions.length; i++) {
            for (int pos : maskPositions[i]) {
                positions[i] |= (1<<pos);
            }
        }

        return createSet(positions);
    }

    ResultKeywordSet createSet(int... positionMasks) {
        var keywords = new SearchResultKeywordScore[positionMasks.length];

        for (int i = 0; i < positionMasks.length; i++) {
            keywords[i] = new SearchResultKeywordScore(0, "",
                    new WordMetadata(0, positionMasks[i], (byte) 0).encode(), 0, false);
        }

        return new ResultKeywordSet(keywords);
    }
}