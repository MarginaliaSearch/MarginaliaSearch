package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

public class SequenceOperations {

    /** Return true if the sequences intersect, false otherwise.
     * */
    public static boolean intersectSequences(IntIterator... sequences) {

        if (sequences.length <= 1)
            return true;

        // Initialize values and find the maximum value
        int[] values = new int[sequences.length];

        for (int i = 0; i < sequences.length; i++) {
            if (sequences[i].hasNext())
                values[i] = sequences[i].nextInt();
            else
                return false;
        }

        // Intersect the sequences by advancing all values smaller than the maximum seen so far
        // until they are equal to the maximum value, or until the end of the sequence is reached
        int max = Integer.MIN_VALUE;
        int successes = 0;
        for (int i = 0; successes < sequences.length; i = (i + 1) % sequences.length)
        {
            if (values[i] == max) {
                successes++;
            } else {
                successes = 1;

                // Discard values until we reach the maximum value seen so far,
                // or until the end of the sequence is reached
                while (values[i] < max) {
                    if (sequences[i].hasNext())
                        values[i] = sequences[i].nextInt();
                    else
                        return false;
                }

                // Update the maximum value, if necessary
                max = Math.max(max, values[i]);
            }
        }

        return true;
    }

    public static IntList findIntersections(IntList... positions) {
        return findIntersections(new int[positions.length], positions);
    }

    public static IntList findIntersections(int[] offsets, IntList... positions) {

        if (positions.length < 1)
            return IntList.of();

        int[] indexes = new int[positions.length];
        // Initialize values and find the maximum value
        int[] values = new int[positions.length];

        for (int i = 0; i < positions.length; i++) {
            if (indexes[i] < positions[i].size())
                values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
            else
                return IntList.of();
        }

        // Intersect the sequences by advancing all values smaller than the maximum seen so far
        // until they are equal to the maximum value, or until the end of the sequence is reached
        int max = Integer.MIN_VALUE;
        int successes = 0;

        IntList ret = new IntArrayList();

        outer:
        for (int i = 0;; i = (i + 1) % positions.length)
        {
            if (successes == positions.length) {
                ret.add(max);
                successes = 1;

                if (indexes[i] < positions[i].size()) {
                    values[i] = positions[i].getInt(indexes[i]++) + offsets[i];

                    // Update the maximum value, if necessary
                    max = Math.max(max, values[i]);
                } else {
                    break;
                }
            } else if (values[i] == max) {
                successes++;
            } else {
                successes = 1;

                // Discard values until we reach the maximum value seen so far,
                // or until the end of the sequence is reached
                while (values[i] < max) {
                    if (indexes[i] < positions[i].size()) {
                        values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
                    } else {
                        break outer;
                    }
                }

                // Update the maximum value, if necessary
                max = Math.max(max, values[i]);
            }
        }

        return ret;
    }

    /** Return the minimum word distance between two sequences, or a negative value if either sequence is empty.
     * */
    public static int minDistance(IntIterator seqA, IntIterator seqB)
    {
        int minDistance = Integer.MAX_VALUE;

        if (!seqA.hasNext() || !seqB.hasNext())
            return -1;

        int a = seqA.nextInt();
        int b = seqB.nextInt();

        while (true) {
            int distance = Math.abs(a - b);
            if (distance < minDistance)
                minDistance = distance;

            if (a <= b) {
                if (seqA.hasNext()) {
                    a = seqA.nextInt();
                } else {
                    break;
                }
            } else {
                if (seqB.hasNext()) {
                    b = seqB.nextInt();
                } else {
                    break;
                }
            }
        }

        return minDistance;
    }

    public static int minDistance(IntList[] positions) {
        return minDistance(positions, new int[positions.length]);
    }

    public static int minDistance(IntList[] positions, int[] offsets) {
        if (positions.length <= 1)
            return 0;

        int[] values = new int[positions.length];
        int[] indexes = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            if (indexes[i] < positions[i].size())
                values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
            else
                return 0;
        }

        int minDist = Integer.MAX_VALUE;

        int minVal = Integer.MAX_VALUE;
        int maxVal = Integer.MIN_VALUE;

        for (int val : values) {
            minVal = Math.min(minVal, val);
            maxVal = Math.max(maxVal, val);
        }

        minDist = Math.min(minDist, maxVal - minVal);

        for (;;) {
            for (int i = 0; i < positions.length; i++) {
                if (values[i] > minVal) {
                    continue;
                }

                if (indexes[i] < positions[i].size()) {
                    values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
                } else {
                    return minDist;
                }

                if (values[i] > maxVal) {
                    maxVal = values[i];
                }

                if (values[i] > minVal) {
                    minVal = Integer.MAX_VALUE;
                    for (int val : values) {
                        minVal = Math.min(minVal, val);
                    }
                }

                minDist = Math.min(minDist, maxVal - minVal);
            }
        }
    }
}
