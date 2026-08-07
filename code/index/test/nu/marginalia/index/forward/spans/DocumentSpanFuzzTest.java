package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fuzzes the galloping span kernels against straightforward reference
 *  implementations of the same merge, mirroring the loops the galloping
 *  versions replaced */
class DocumentSpanFuzzTest {

    @Test
    public void fuzzAgainstReference() {
        Random rng = new Random(1);

        for (int round = 0; round < 100_000; round++) {
            IntList startsEnds = randomSpans(rng);

            // Dense rounds exercise the search oriented counting path, which
            // only engages when the position list dwarfs the span list
            IntList positions = (round % 4 == 0) ? densePositions(rng) : randomPositions(rng);

            // Zero length ranges have order dependent matching semantics in the
            // production loops and never occur, constraint groups have size >= 1
            int len = 1 + rng.nextInt(4);

            DocumentSpan span = new DocumentSpan(startsEnds);

            String desc = "round " + round + ", spans " + startsEnds + ", positions " + positions + ", len " + len;

            assertEquals(refCountIntersections(startsEnds, positions), span.countIntersections(positions), desc);
            assertEquals(refContainsRange(startsEnds, positions, len), span.containsRange(positions, len), desc);
            assertEquals(refCountRangeMatches(startsEnds, positions, len), span.countRangeMatches(positions, len), desc);
            assertEquals(refCountRangeMatchesAtBoundary(startsEnds, positions, len), span.countRangeMatchesAtBoundary(positions, len), desc);
            assertEquals(refCountRangeMatchesExact(startsEnds, positions, len), span.countRangeMatchesExact(positions, len), desc);
        }
    }

    /** Sorted non-overlapping spans over a small position range, so that
     *  adjacent spans and positions at exact span edges occur frequently */
    private IntList randomSpans(Random rng) {
        IntArrayList startsEnds = new IntArrayList();

        int spanCount = rng.nextInt(12);
        int pos = rng.nextInt(4);

        for (int i = 0; i < spanCount; i++) {
            pos += rng.nextInt(5);
            int len = 1 + rng.nextInt(6);

            startsEnds.add(pos);
            startsEnds.add(pos + len);

            pos += len;
        }

        return startsEnds;
    }

    private IntList randomPositions(Random rng) {
        IntArrayList positions = new IntArrayList();

        int count = rng.nextInt(20);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            pos += 1 + rng.nextInt(6);
            positions.add(pos);
        }

        return positions;
    }

    private IntList densePositions(Random rng) {
        IntArrayList positions = new IntArrayList();

        int count = rng.nextInt(600);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            pos += 1 + rng.nextInt(3);
            positions.add(pos);
        }

        return positions;
    }

    private int refCountIntersections(IntList startsEnds, IntList positions) {
        if (startsEnds.isEmpty() || positions.isEmpty())
            return 0;

        int cnt = 0;
        for (int pi = 0; pi < positions.size(); pi++) {
            int pos = positions.getInt(pi);
            for (int sei = 0; sei < startsEnds.size(); sei += 2) {
                if (pos >= startsEnds.getInt(sei) && pos < startsEnds.getInt(sei + 1)) {
                    cnt++;
                    break;
                }
            }
        }
        return cnt;
    }

    /** Like the production kernels, a position is only ever tested against the
     *  first span whose end lies beyond it.  For len >= 1 this candidate rule is
     *  equivalent to the production merge loops. */
    private boolean refContainsRange(IntList startsEnds, IntList positions, int len) {
        if (startsEnds.size() < 2 || positions.isEmpty())
            return false;

        for (int pi = 0; pi < positions.size(); pi++) {
            int pos = positions.getInt(pi);
            for (int sei = 0; sei < startsEnds.size(); sei += 2) {
                int end = startsEnds.getInt(sei + 1);
                if (pos >= end)
                    continue;

                if (pos >= startsEnds.getInt(sei) && pos + len <= end) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private int refCountRangeMatches(IntList startsEnds, IntList positions, int len) {
        if (startsEnds.size() < 2 || positions.isEmpty())
            return 0;

        int cnt = 0;
        for (int pi = 0; pi < positions.size(); pi++) {
            int pos = positions.getInt(pi);
            for (int sei = 0; sei < startsEnds.size(); sei += 2) {
                int end = startsEnds.getInt(sei + 1);
                if (pos >= end)
                    continue;

                if (pos >= startsEnds.getInt(sei) && pos + len <= end) {
                    cnt++;
                }
                break;
            }
        }
        return cnt;
    }

    private int refCountRangeMatchesAtBoundary(IntList startsEnds, IntList positions, int len) {
        if (startsEnds.size() < 2 || positions.isEmpty())
            return 0;

        int cnt = 0;
        for (int pi = 0; pi < positions.size(); pi++) {
            int pos = positions.getInt(pi);
            for (int sei = 0; sei < startsEnds.size(); sei += 2) {
                int start = startsEnds.getInt(sei);
                int end = startsEnds.getInt(sei + 1);
                if (pos >= end)
                    continue;

                if (pos >= start && pos + len <= end
                        && (pos == start || pos + len == end)) {
                    cnt++;
                }
                break;
            }
        }
        return cnt;
    }

    private int refCountRangeMatchesExact(IntList startsEnds, IntList positions, int len) {
        if (startsEnds.size() < 2 || positions.isEmpty())
            return 0;

        int cnt = 0;
        int sei = 0;
        for (int pi = 0; pi < positions.size() && sei < startsEnds.size(); pi++) {
            int pos = positions.getInt(pi);
            while (sei < startsEnds.size()) {
                int start = startsEnds.getInt(sei);
                int end = startsEnds.getInt(sei + 1);

                if (pos == start && pos + len == end) {
                    cnt++;
                    sei += 2;
                    break;
                }
                else if (pos < end) {
                    break;
                }
                else {
                    sei += 2;
                }
            }
        }
        return cnt;
    }
}
