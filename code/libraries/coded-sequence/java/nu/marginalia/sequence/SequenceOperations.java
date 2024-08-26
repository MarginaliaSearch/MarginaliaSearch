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

    /** Find any intersections between the given positions lists, and return the list of intersections.
     * If any of the lists are empty, return an empty list.
     * <p></p>
     */
    public static IntList findIntersections(IntList... positions) {
        return findIntersections(positions, new int[positions.length]);
    }

    /** Find any intersections between the given positions lists, and return the list of intersections.
     * If any of the lists are empty, return an empty list.
     * <p></p>
     * A constant offset can be applied to each position list by providing an array of offsets.
     *
     * @param positions the positions lists to compare - each list must be sorted in ascending order
     *                  and contain unique values.
     * @param offsets constant offsets to apply to each position
     * */
    public static IntList findIntersections(IntList[] positions, int[] offsets) {

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


    /** Given each set of positions, one from each list, find the set with the smallest distance between them
     * and return that distance.  If any of the lists are empty, return 0.
     * */
    public static int minDistance(IntList[] positions) {
        return minDistance(positions, new int[positions.length]);
    }

    /** Given each set of positions, one from each list, find the set with the smallest distance between them
     * and return that distance.  If any of the lists are empty, return 0.
     *
     * @param positions the positions lists to compare - each list must be sorted in ascending order
     * @param offsets the offsets to apply to each position
     */
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

        int maxVal = Integer.MIN_VALUE;

        int maxI = 0;

        // Find the maximum value in values[] and its index in positions[]
        for (int i = 0; i < positions.length; i++) {
            if (values[i] > maxVal) {
                maxVal = values[i];
                maxI = i;
            }
        }

        for (;;) {
            // For all the other indexes except maxI, update values[] with the largest value smaller than maxVal
            for (int idx = 0; idx < positions.length - 1; idx++) {
                int i = (maxI + idx) % positions.length;

                // Update values[i] until it is the largest value smaller than maxVal

                int len = positions[i].size();
                int offset = offsets[i];
                int prevValue = values[i];
                int value = prevValue;

                while (indexes[i] < len) {
                    prevValue = value;
                    value = positions[i].getInt(indexes[i]++) + offset;
                    if (value >= maxVal) {
                        indexes[i]--; // correct for overshooting the largest value smaller than maxVal
                        break;
                    }
                }

                values[i] = prevValue;
            }

            // Calculate minVal and update minDist
            int minVal = Integer.MAX_VALUE;
            for (int val : values) {
                minVal = Math.min(minVal, val);
            }
            minDist = Math.min(minDist, maxVal - minVal);


            // Find the next maximum value and its index.  We look for the largest value smaller than the current maxVal,
            // which is the next target value
            maxVal = Integer.MAX_VALUE;

            for (int i = 0; i < positions.length; i++) {
                int index = indexes[i];
                if (index >= positions[i].size()) { // no more values in this list, skip
                    continue;
                }

                int value =  positions[i].getInt(index) + offsets[i];
                if (value < maxVal) {
                    maxVal = value;
                    maxI = i;
                }
            }

            if (maxVal != Integer.MAX_VALUE) {
                indexes[maxI]++;
            }
            else {
                return minDist;
            }
        }
    }
}
