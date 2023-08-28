package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;

import java.util.function.LongBinaryOperator;


/** Functions for operating on pairs of arrays.
 */
public class TwoArrayOperations {

    /**
     * Merge two sorted arrays into a third array, removing duplicates.
     */
    public static long mergeArrays(LongArray out, LongArray a, LongArray b, long outStart, long outEnd, long aStart, long aEnd, long bStart, long bEnd) {

        // Ensure that the arrays are sorted and that the output array is large enough
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSorted(aStart, aEnd));
            assert (b.isSorted(bStart, bEnd));
            assert ((outEnd - outStart) >= countDistinctElements(a, b, aStart, aEnd, bStart, bEnd));
        }

        // Try to get direct access to the arrays if possible, this an order of magnitude faster
        var directRangeA = a.directRangeIfPossible(aStart, aEnd);
        var directRangeB = b.directRangeIfPossible(bStart, bEnd);
        var directRangeOut = out.directRangeIfPossible(outStart, outEnd);

        return mergeArraysDirect(directRangeOut.array(), directRangeA.array(), directRangeB.array(),
                directRangeOut.start(), directRangeA.start(), directRangeA.end(), directRangeB.start(), directRangeB.end());
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
    public static void mergeArrays2(LongArray out, LongArray a, LongArray b,
                                    long outStart, long outEnd,
                                    long aStart, long aEnd,
                                    long bStart, long bEnd)
    {
        // Ensure that the arrays are sorted and that the output array is large enough
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSortedN(2, aStart, aEnd));
            assert (b.isSortedN(2, bStart, bEnd));
            assert ((outEnd - outStart) == 2 * countDistinctElementsN(2, a, b, aStart, aEnd, bStart, bEnd));
        }

        // Try to get direct access to the arrays if possible, this an order of magnitude faster
        var directRangeA = a.directRangeIfPossible(aStart, aEnd);
        var directRangeB = b.directRangeIfPossible(bStart, bEnd);
        var directRangeOut = out.directRangeIfPossible(outStart, outEnd);

        mergeArraysDirect2(directRangeOut.array(), directRangeA.array(), directRangeB.array(),
                           directRangeOut.start(),
                           directRangeA.start(), directRangeA.end(),
                           directRangeB.start(), directRangeB.end());
    }

    /** For each value in the source array, merge it with the corresponding value in the destination array.
     *
     */
    public static void mergeArrayValues(LongArray dest, LongArray source, LongBinaryOperator mergeFunction, long destStart, long destEnd, long sourceStart, long sourceEnd) {

        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (dest.isSortedN(2, destStart, destEnd));
            assert (source.isSortedN(2, sourceStart, sourceEnd));
        }

        // Try to get direct access to the arrays if possible, this an order of magnitude faster
        var destRange = dest.directRangeIfPossible(destStart, destEnd);
        var sourceRange = source.directRangeIfPossible(sourceStart, sourceEnd);

        mergeArrayValuesDirect(
                destRange.array(), sourceRange.array(),
                mergeFunction,
                destRange.start(), destRange.end(),
                sourceRange.start(), sourceRange.end());
    }

    private static void mergeArrayValuesDirect(LongArray dest, LongArray source, LongBinaryOperator mergeFunction, long destStart, long destEnd, long sourceStart, long sourceEnd) {

        long destPos = destStart;
        long sourcePos = sourceStart;

        while (destPos < destEnd && sourcePos < sourceEnd) {
            long destVal = dest.get(destPos);
            long sourceVal = source.get(sourcePos);

            if (destVal < sourceVal) {
                destPos += 2;
            } else if (sourceVal < destVal) {
                sourcePos += 2;
            } else {
                long mergedVal = mergeFunction.applyAsLong(dest.get(destPos + 1), source.get(sourcePos + 1));
                dest.set(destPos + 1, mergedVal);

                destPos += 2;
                sourcePos += 2;
            }
        }

    }

    private static long mergeArraysDirect(LongArray out,
                                          LongArray a, LongArray b,
                                          long outStart,
                                          long aStart, long aEnd,
                                          long bStart, long bEnd) {
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
     * Merge two sorted arrays into a third array, step size 2, removing duplicates.
     * <p>
     * It will prefer the first array if there are duplicates.
     */
    private static void mergeArraysDirect2(LongArray out, LongArray a, LongArray b, long outStart, long aStart, long aEnd, long bStart, long bEnd) {
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
    }



    /**
     * Count the number of distinct elements in two sorted arrays.
     */
    public static long countDistinctElements(LongArray a, LongArray b, long aStart, long aEnd, long bStart, long bEnd) {
        var directRangeA = a.directRangeIfPossible(aStart, aEnd);
        var directRangeB = b.directRangeIfPossible(bStart, bEnd);

        // Ensure that the arrays are sorted
        if (TwoArrayOperations.class.desiredAssertionStatus()) {
            assert (a.isSorted(aStart, aEnd));
            assert (b.isSorted(bStart, bEnd));
        }

        a = directRangeA.array();
        aStart = directRangeA.start();
        aEnd = directRangeA.end();

        b = directRangeB.array();
        bStart = directRangeB.start();
        bEnd = directRangeB.end();

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

        var directRangeA = a.directRangeIfPossible(aStart, aEnd);
        var directRangeB = b.directRangeIfPossible(bStart, bEnd);

        return countDistinctElementsDirectN(stepSize,
                directRangeA.array(),
                directRangeB.array(),
                directRangeA.start(),
                directRangeA.end(),
                directRangeB.start(),
                directRangeB.end());
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
