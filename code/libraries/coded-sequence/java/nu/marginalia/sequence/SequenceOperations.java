package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

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

    public static IntList findIntersections(IntIterator... sequences) {

        if (sequences.length <= 1)
            return IntList.of();

        // Initialize values and find the maximum value
        int[] values = new int[sequences.length];

        for (int i = 0; i < sequences.length; i++) {
            if (sequences[i].hasNext())
                values[i] = sequences[i].nextInt();
            else
                return IntList.of();
        }

        // Intersect the sequences by advancing all values smaller than the maximum seen so far
        // until they are equal to the maximum value, or until the end of the sequence is reached
        int max = Integer.MIN_VALUE;
        int successes = 0;

        IntList ret = new IntArrayList();

        outer:
        for (int i = 0;; i = (i + 1) % sequences.length)
        {
            if (successes == sequences.length) {
                ret.add(max);
                successes = 1;

                if (sequences[i].hasNext()) {
                    max = sequences[i].nextInt();
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
                    if (sequences[i].hasNext()) {
                        values[i] = sequences[i].nextInt();
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

    public static int minDistance(List<IntIterator> iterators) {
        if (iterators.size() <= 1)
            return 0;

        int[] values = new int[iterators.size()];

        for (int i = 0; i < iterators.size(); i++) {
            if (iterators.get(i).hasNext())
                values[i] = iterators.get(i).nextInt();
            else
                return 0;
        }

        int minDist = Integer.MAX_VALUE;
        int successes = 0;

        int minVal = Integer.MAX_VALUE;
        int maxVal = Integer.MIN_VALUE;

        for (int val : values) {
            minVal = Math.min(minVal, val);
            maxVal = Math.max(maxVal, val);
        }

        minDist = Math.min(minDist, maxVal - minVal);

        for (int i = 0; successes < iterators.size(); i = (i + 1) % iterators.size())
        {
            if (values[i] == minVal) {
                if (!iterators.get(i).hasNext()) {
                    break;
                }
                values[i] = iterators.get(i).nextInt();

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

        return minDist;
    }
}
