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

    /** Return true if the sequences intersect, false otherwise.
     * */
    public static boolean intersectOffsetSequences(IntIterator[] sequences, int[] offsets) {

        if (sequences.length <= 1)
            return true;

        // Initialize values and find the maximum value
        int[] values = new int[sequences.length];

        for (int i = 0; i < sequences.length; i++) {
            if (sequences[i].hasNext())
                values[i] = sequences[i].nextInt() + offsets[i];
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
                        values[i] = sequences[i].nextInt() + offsets[i];
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
    public static IntList findIntersections(IntArrayList ret, IntList... positions) {
        return findIntersections(ret, positions, new int[positions.length], Integer.MAX_VALUE);
    }

    /** Find any intersections between the given positions lists, and return the list of intersections.
     * If any of the lists are empty, return an empty list.
     * <p></p>
     * A constant offset can be applied to each position list by providing an array of offsets.
     *
     * @param positions the positions lists to compare - each list must be sorted in ascending order
     *                  and contain unique values.
     * @param offsets constant offsets to apply to each position
     * @param n       maximum number of intersections we're interested in.  The algorithm does not guarantee
     *                the return value will have a smaller size than this if it is cheaper to return back e.g.
     *                an input list.
     * */
    public static IntList findIntersections(IntArrayList ret, IntList[] positions, int[] offsets, int n) {

        // Trivial cases
        if (positions.length < 1) { // n = 0
            return ret;
        }

        // Lists are typically bimodal shaped, a couple of positions for most terms
        // with the odd outlier list in the hundreds.
        int minIdx = 0;
        int totalSize = 0;
        for (int i = 0; i < positions.length; i++) {
            int size = positions[i].size();
            if (size == 0)
                return IntList.of();

            totalSize += size;
            if (size < positions[minIdx].size())
                minIdx = i;
        }

        int minSize = positions[minIdx].size();
        if (totalSize - minSize >= PROBE_ORIENTATION_CUTOFF * minSize * (positions.length - 1)) {
            return findIntersectionsByProbe(ret, positions, offsets, n, minIdx);
        }

        int[] indexes = new int[positions.length];
        // Initialize values and find the maximum value
        int[] values = new int[positions.length];
        int minLength = Integer.MAX_VALUE;
        int largestValue = Integer.MAX_VALUE;

        for (int i = 0; i < positions.length; i++) {
            minLength = Math.min(minLength, positions[i].size());

            if (indexes[i] < positions[i].size())
                values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
            else
                return IntList.of();

            largestValue = Math.min(largestValue, positions[i].getInt(positions[i].size() - 1) + offsets[i]);
        }

        // Intersect the sequences by advancing all values smaller than the maximum seen so far
        // until they are equal to the maximum value, or until the end of the sequence is reached
        int currentMax = Integer.MIN_VALUE;

        int listMatches = 0;
        int foundIntersections = 0;

        ret.ensureCapacity(Math.min(n, Math.max(1, minLength)));

        outer:
        for (int i = 0; currentMax <= largestValue; i = (i + 1 == positions.length) ? 0 : i + 1)
        {
            if (listMatches == positions.length) {
                ret.add(currentMax);
                if (++foundIntersections > n) return ret;

                listMatches = 1;

                if (indexes[i] < positions[i].size()) {
                    values[i] = positions[i].getInt(indexes[i]++) + offsets[i];

                    // Update the maximum value, if necessary
                    currentMax = Math.max(currentMax, values[i]);
                } else {
                    break;
                }
            } else if (values[i] == currentMax) {
                listMatches++;
            } else {
                listMatches = 1;

                // Discard values until we reach the maximum value seen so far,
                // or until the end of the sequence is reached
                while (values[i] < currentMax) {
                    if (indexes[i] < positions[i].size()) {
                        values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
                    } else {
                        break outer;
                    }
                }

                // Update the maximum value, if necessary
                currentMax = Math.max(currentMax, values[i]);
            }
        }

        return ret;
    }

    /** Ratio between the smallest list and the average of the others at which
     *  findIntersections switches from the merge walk to probing.  Calibrated
     *  against sampled ranking data. */
    private static final int PROBE_ORIENTATION_CUTOFF = 8;

    /** Intersect by walking the smallest list and binary searching the other
     *  lists for each of its values.  Search windows start at the previous hit
     *  since the probed values are ascending. */
    private static IntList findIntersectionsByProbe(IntArrayList ret, IntList[] positions, int[] offsets, int n, int minIdx) {
        IntList driver = positions[minIdx];
        int driverOffset = offsets[minIdx];
        int driverSize = driver.size();

        int[] fronts = new int[positions.length];
        int found = 0;

        ret.ensureCapacity(Math.min(n, driverSize));

        outer:
        for (int di = 0; di < driverSize; di++) {
            int value = driver.getInt(di) + driverOffset;

            for (int i = 0; i < positions.length; i++) {
                if (i == minIdx)
                    continue;

                int target = value - offsets[i];
                int idx = firstIndexAtOrAbove(positions[i], target, fronts[i]);
                if (idx == positions[i].size())
                    break outer;

                fronts[i] = idx;
                if (positions[i].getInt(idx) != target)
                    continue outer;
            }

            ret.add(value);
            if (++found > n)
                return ret;
        }

        return ret;
    }

    private static int firstIndexAtOrAbove(IntList list, int key, int from) {
        int lo = from;
        int hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.getInt(mid) < key) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Given each set of positions, one from each list, find the set with the smallest distance between them
     * and return that distance.  If any of the lists are empty, return 0.
     * */
    public static int minDistance(IntList[] positions) {
        return minDistance(positions, new int[positions.length]);
    }

    /** Given each set of positions, one from each list, find the set with the smallest distance between them
     * and return that distance.  If any of the lists are empty, return Integer.MAX_VALUE.
     *
     * @param positions the positions lists to compare - each list must be sorted in ascending order
     * @param offsets the offsets to apply to each position
     */
    public static int minDistance(IntList[] positions, int[] offsets) {
        return minDistance(positions, offsets, positions.length);
    }

    /** As {@link #minDistance(IntList[], int[])}, considering only the first k
     *  entries of each array.  Lets callers that filter their inputs reuse a
     *  partially filled array. */
    public static int minDistance(IntList[] positions, int[] offsets, int k) {
        if (k <= 1)
            return 0;

        int[] values = new int[k];
        int[] indexes = new int[k];

        int largestValue = Integer.MAX_VALUE;

        for (int i = 0; i < k; i++) {
            // if any of the lists are empty, return MAX_VALUE

            if (positions[i].isEmpty()) {
                return Integer.MAX_VALUE;
            }

            values[i] = positions[i].getInt(indexes[i]++) + offsets[i];
            largestValue = Math.min(largestValue, positions[i].getInt(positions[i].size() - 1) + offsets[i]);
        }

        int minDist = Integer.MAX_VALUE;
        int maxVal = Integer.MIN_VALUE;

        int maxI = 0;

        // Find the maximum value in values[] and its index in positions[]
        for (int i = 0; i < k; i++) {
            if (values[i] > maxVal) {
                maxVal = values[i];
                maxI = i;
            }
        }

        do {
            // For all the other indexes except maxI, update values[] with the largest value smaller than maxVal
            for (int idx = 1; idx < k; idx++) {
                int i = (maxI + idx) % k;

                // Update values[i] to the largest value smaller than maxVal

                int len = positions[i].size();
                int offset = offsets[i];
                int prevValue = values[i];
                int value = prevValue;

                while (indexes[i] < len) {
                    prevValue = value;
                    value = positions[i].getInt(indexes[i]++) + offset;
                    if (value >= maxVal) {
                        indexes[i]--; // correct for overshooting
                        break;
                    }
                }

                values[i] = (value >= maxVal) ? prevValue : value;
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

            for (int i = 0; i < k; i++) {
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
                values[maxI] = maxVal;
                indexes[maxI]++;
            }
            else {
                return minDist;
            }
        } while (maxVal <= largestValue);

        return minDist;
    }
}
