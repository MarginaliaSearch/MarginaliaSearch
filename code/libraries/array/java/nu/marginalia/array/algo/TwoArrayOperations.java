package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;


/** Functions for operating on pairs of arrays.
 */
public class TwoArrayOperations {

    /**
     * Merge two sorted arrays into a third array, removing duplicates.
     */
    public static long mergeArrays(LongArray out, LongArray a, LongArray b, long outStart, long aStart, long aEnd, long bStart, long bEnd) {
        // Ensure that the arrays are sorted and that the output array is large enough
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSorted(aStart, aEnd));
            assert (b.isSorted(bStart, bEnd));
        }

        long aPos = aStart;
        long bPos = bStart;
        long outPos = outStart;

        long lastValue = 0;

        while (aPos < aEnd && bPos < bEnd) {
            final long aVal = a.get(aPos);
            final long bVal = b.get(bPos);
            final long setVal;

            if (aVal < bVal) {
                setVal = aVal;
                aPos++;
            } else if (bVal < aVal) {
                setVal = bVal;
                bPos++;
            } else {
                setVal = aVal;
                aPos++;
                bPos++;
            }

            if (outPos == outStart || setVal != lastValue) {
                out.set(outPos++, setVal);
            }

            lastValue = setVal;
        }

        while (aPos < aEnd) {
            long val = a.get(aPos++);

            if (val != lastValue || outPos == outStart) {
                out.set(outPos++, val);
            }

            lastValue = val;
        }

        while (bPos < bEnd) {
            long val = b.get(bPos++);

            if (val != lastValue || outPos == outStart) {
                out.set(outPos++, val);
            }

            lastValue = val;
        }

        return outPos - outStart;
    }

    /**
     * Merge two sorted arrays into a third array, removing duplicates.
     * <p>
     * The operation is performed with a step size of 2. For each pair of values,
     * only the first is considered to signify a key. The second value is retained along
     * with the first.  In the case of a duplicate, the value associated with array 'a'
     * is retained, the other is discarded.
     *
     */
    public static long mergeArrays2(LongArray out, LongArray a, LongArray b,
                                    long outStart,
                                    long aStart, long aEnd,
                                    long bStart, long bEnd)
    {
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSortedN(2, aStart, aEnd));
            assert (b.isSortedN(2, bStart, bEnd));
        }

        long aPos = aStart;
        long bPos = bStart;
        long outPos = outStart;

        long lastValue = 0;

        while (aPos < aEnd && bPos < bEnd) {
            final long aVal = a.get(aPos);
            final long bVal = b.get(bPos);

            final long setVal;
            final long setArg;

            if (aVal < bVal) {
                setVal = aVal;
                setArg = a.get(aPos + 1);

                aPos+=2;
            } else if (bVal < aVal) {
                setVal = bVal;
                setArg = b.get(bPos + 1);

                bPos+=2;
            } else {
                setVal = aVal;
                setArg = a.get(aPos + 1);

                aPos+=2;
                bPos+=2;
            }

            if (setVal != lastValue || outPos == outStart) {
                out.set(outPos++, setVal);
                out.set(outPos++, setArg);

                lastValue = setVal;
            }
        }

        while (aPos < aEnd) {
            long val = a.get(aPos++);
            long arg = a.get(aPos++);

            if (val != lastValue || outPos == outStart) {
                out.set(outPos++, val);
                out.set(outPos++, arg);
                lastValue = val;
            }
        }

        while (bPos < bEnd) {
            long val = b.get(bPos++);
            long arg = b.get(bPos++);

            if (val != lastValue || outPos == outStart) {
                out.set(outPos++, val);
                out.set(outPos++, arg);

                lastValue = val;
            }
        }

        return outPos - outStart;
    }

    /**
     * Count the number of distinct elements in two sorted arrays.
     */
    public static long countDistinctElements(LongArray a, LongArray b, long aStart, long aEnd, long bStart, long bEnd) {
        // Ensure that the arrays are sorted
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSorted(aStart, aEnd));
            assert (b.isSorted(bStart, bEnd));
        }

        return countDistinctElementsDirect(a, b, aStart, aEnd, bStart, bEnd);
    }

    /**
     * Count the number of distinct elements in two sorted arrays with step size 2. Only consider the first element of each pair.
     */
    public static long countDistinctElementsN(int stepSize, LongArray a, LongArray b, long aStart, long aEnd, long bStart, long bEnd) {
        // Ensure that the arrays are sorted
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSortedN(stepSize, aStart, aEnd));
            assert (b.isSortedN(stepSize, bStart, bEnd));
        }

        return countDistinctElementsDirectN(stepSize,
                a,
                b,
                aStart,
                aEnd,
                bStart,
                bEnd);
    }

    private static long countDistinctElementsDirect(LongArray a, LongArray b, long aStart, long aEnd, long bStart, long bEnd) {
        long aPos = aStart;
        long bPos = bStart;

        long distinct = 0;
        long lastValue = 0;

        while (aPos < aEnd && bPos < bEnd) {
            final long aVal = a.get(aPos);
            final long bVal = b.get(bPos);
            final long setVal;

            if (aVal < bVal) {
                setVal = aVal;
                aPos++;
            } else if (bVal < aVal) {
                setVal = bVal;
                bPos++;
            } else {
                setVal = aVal;
                aPos++;
                bPos++;
            }

            if (distinct == 0 || (setVal != lastValue)) {
                distinct++;
            }

            lastValue = setVal;
        }

        while (aPos < aEnd) {
            long val = a.get(aPos++);

            if (distinct == 0 || (val != lastValue)) {
                distinct++;
            }
            lastValue = val;
        }

        while (bPos < bEnd) {
            long val = b.get(bPos++);

            if (distinct == 0 || (val != lastValue)) {
                distinct++;
            }
            lastValue = val;
        }

        return distinct;
    }

    private static long countDistinctElementsDirectN(int stepSize, LongArray a, LongArray b, long aStart, long aEnd, long bStart, long bEnd) {
        long aPos = aStart;
        long bPos = bStart;

        long distinct = 0;
        long lastValue = 0;

        while (aPos < aEnd && bPos < bEnd) {
            long aVal = a.get(aPos);
            long bVal = b.get(bPos);

            final long val;
            if (aVal < bVal) {
                val = aVal;

                aPos+=stepSize;
            } else if (bVal < aVal) {
                val = bVal;

                bPos+=stepSize;
            } else {
                val = aVal;

                aPos+=stepSize;
                bPos+=stepSize;
            }

            if (distinct == 0 || (val != lastValue)) {
                distinct++;
            }

            lastValue = val;
        }

        while (aPos < aEnd) {
            long val = a.get(aPos);
            aPos+=stepSize;
            if (distinct == 0 || val != lastValue) {
                distinct++;
            }
            lastValue = val;
        }

        while (bPos < bEnd) {
            long val = b.get(bPos);
            bPos+=stepSize;
            if (distinct == 0 || val != lastValue) {
                distinct++;
            }
            lastValue = val;
        }

        return distinct;
    }
}
