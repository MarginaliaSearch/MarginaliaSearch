package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SequenceOperationsTest {

    @Test
    void intersectSequencesSingle() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator()));
    }

    @Test
    void intersectSequencesTrivialMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 1);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesTrivialMismatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2);

        assertFalse(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesOffsetMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 3);

        assertTrue(SequenceOperations.intersectSequences(seq1.offsetIterator(0), seq2.offsetIterator(-2)));
    }

    @Test
    void intersectSequencesDeepMatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 14);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void intersectSequencesDeepMatch3() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 14);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 1, 5, 8, 9);

        assertTrue(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator(), seq3.iterator()));
    }

    @Test
    void intersectSequencesDeepMatch3findIntersections() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 10, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 8, 10, 14);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 1, 5, 8, 9, 10);

        assertEquals(IntList.of(8, 10), SequenceOperations.findIntersections(iterOffsets, seq1.iterator(), seq2.iterator(), seq3.iterator()));
    }


    @Test
    void intersectSequencesDeepMismatch() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 1, 3, 4, 7, 8, 9, 11);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 2, 5, 14);

        assertFalse(SequenceOperations.intersectSequences(seq1.iterator(), seq2.iterator()));
    }

    @Test
    void testMinDistance() {
        ByteBuffer wa = ByteBuffer.allocate(1024);
        GammaCodedSequence seq1 = GammaCodedSequence.generate(wa, 11, 80, 160);
        GammaCodedSequence seq2 = GammaCodedSequence.generate(wa, 20, 50, 100);
        GammaCodedSequence seq3 = GammaCodedSequence.generate(wa, 30, 60, 90);

        assertEquals(19, SequenceOperations.minDistance(new IntIterator[]{seq1.iterator(), seq2.iterator(), seq3.iterator()}));
    }
}