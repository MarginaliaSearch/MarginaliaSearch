package nu.marginalia.adjacencies;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.roaringbitmap.RoaringBitmap;


/** This is a special implementation of a sparse bit vector for consine similarity calculations
 * <br>
 * It makes assumptions about the nature of the data that are not generally valid.
 */
public class SparseBitVector {
    final TIntArrayList backingList;
    long hash;

    public SparseBitVector() {
        backingList = new TIntArrayList(16);
        backingList.sort();
    }

    public static SparseBitVector of(int... list) {
        var set = new TIntHashSet(list);
        TIntArrayList lst = new TIntArrayList(set);
        lst.sort();

        return new SparseBitVector(lst);
    }

    public static SparseBitVector of(RoaringBitmap bmap) {

        TIntArrayList lst = new TIntArrayList(bmap.getCardinality());

        lst.addAll(bmap.toArray());

        return new SparseBitVector(lst);
    }


    private SparseBitVector(TIntArrayList list) {
        backingList = list;
        hash = 0;

        if (list.size() < 32) {
            for (int v : list.toArray()) {
                int bit = hasher.hashInt(v).asInt() % 64;
                hash |= (1L << bit);
            }
        }
        else {
            hash = ~0L;
        }

    }

    private static final HashFunction hasher = Hashing.murmur3_128(0);

    public boolean add(int val) {
        if (!contains(val)) {
            return false;
        }

        if (backingList.size() < 32) {
            int bit = hasher.hashInt(val).asInt() % 64;
            hash |= (1L << bit);
        }
        else {
            hash = ~0L;
        }
        backingList.add(val);
        backingList.sort();
        return true;
    }

    public boolean contains(int val) {
        return backingList.binarySearch(val) >= 0;
    }

    public int getCardinality() {
        return backingList.size();
    }

    public static int andCardinality(SparseBitVector a, SparseBitVector b) {

        if (!testHash(a,b)) {
            return 0;
        }

        int aCard = a.getCardinality();
        int bCard = b.getCardinality();

        if (aCard / bCard > 2) {
            return andBigSmall(a, b);
        }
        else if (bCard / aCard > 2) {
            return andBigSmall(b, a);
        }

        return andLinear(a,b);
    }

    private static int andLinearSmall(SparseBitVector a, SparseBitVector b) {
        int sum = 0;
        for (int i = 0; i < a.getCardinality(); i++) {
            for (int j = 0; j < b.getCardinality(); j++) {
                if (a.backingList.getQuick(i) == b.backingList.getQuick(j))
                    sum++;
            }
        }
        return sum;
    }

    private static int andLinear(SparseBitVector a, SparseBitVector b) {

        int i = 0, j = 0;
        int card = 0;

        do {
            int diff = a.backingList.getQuick(i) - b.backingList.getQuick(j);

            if (diff < 0) i++;
            else if (diff > 0) j++;
            else {
                i++;
                j++;
                card++;
            }
        } while (i < a.getCardinality() && j < b.getCardinality());

        return card;

    }

    private static boolean testHash(SparseBitVector a, SparseBitVector b) {
        return (a.hash & b.hash) != 0;
    }

    public boolean cardinalityExceeds(int val) {
        return getCardinality() >= val;
    }

    public static SparseBitVector and(SparseBitVector a, SparseBitVector b) {
        int i = 0;
        int j = 0;

        TIntArrayList andVals = new TIntArrayList(1 + (int)Math.sqrt(a.getCardinality()));

        while (i < a.getCardinality() && j < b.getCardinality()) {
            int diff = a.backingList.getQuick(i) - b.backingList.getQuick(j);
            if (diff < 0) i++;
            else if (diff > 0) j++;
            else {
                andVals.add(a.backingList.getQuick(i));
                i++;
                j++;
            }
        }

        return new SparseBitVector(andVals);
    }

    public static double weightedProduct(float[] weights, SparseBitVector a, SparseBitVector b) {
        int i = 0;
        int j = 0;

        double sum = 0;
        int aCard = a.getCardinality();
        int bCard = b.getCardinality();

        if (aCard == 0 || bCard == 0) return 0.;


        if (aCard + bCard < 10) {
            return weightedProductSmall(weights, a, b);
        }

        if (aCard / bCard > 2) {
            return weightedProductBigSmall(weights, a, b);
        }
        else if (bCard / aCard > 2) {
            return weightedProductBigSmall(weights, b, a);
        }

        do {
            int diff = a.backingList.getQuick(i) - b.backingList.getQuick(j);
            if (diff < 0) i++;
            else if (diff > 0) j++;
            else {
                sum += weights[a.backingList.getQuick(i)];
                i++;
                j++;
            }

        } while (i < a.getCardinality() && j < b.getCardinality());

        return sum;
    }


    private static double weightedProductSmall(float[] weights, SparseBitVector a, SparseBitVector b) {
        double sum = 0;

        for (int i = 0; i < a.getCardinality(); i++) {
            for (int j = 0; j < b.getCardinality(); j++) {
                int av = a.backingList.getQuick(i);
                int bv = b.backingList.getQuick(j);
                if (av == bv)
                    sum+=weights[av];
            }
        }

        return sum;
    }

    private static int andBigSmall(SparseBitVector aBig, SparseBitVector bSmall) {
        int cnt = 0;

        final var smallList = bSmall.backingList;
        final var bigList = aBig.backingList;

        for (int i = 0; i < smallList.size(); i++) {
            int v = smallList.getQuick(i);

            if (bigList.binarySearch(v) >= 0) {
                cnt++;
            }
        }

        return cnt;
    }

    private static double weightedProductBigSmall(float[] weights, SparseBitVector aBig, SparseBitVector bSmall) {
        double sum = 0;

        final var smallList = bSmall.backingList;
        final var bigList = aBig.backingList;

        for (int i = 0; i < smallList.size(); i++) {
            int v = smallList.getQuick(i);

            if (bigList.binarySearch(v) >= 0) {
                sum+=weights[v];
            }
        }

        return sum;
    }

    public double mulAndSum(float[] weights) {
        double sum = 0;
        for (int i = 0; i < backingList.size(); i++) {
            sum += weights[backingList.getQuick(i)];
        }
        return sum;
    }
    public int[] toArray() {
        return backingList.toArray();
    }

    public TIntArrayList values() {
        return backingList;
    }
}
