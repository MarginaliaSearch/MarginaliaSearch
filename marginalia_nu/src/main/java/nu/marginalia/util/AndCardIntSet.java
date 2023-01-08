package nu.marginalia.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.roaringbitmap.RoaringBitmap;


public class AndCardIntSet  {
    final TIntArrayList backingList;
    long hash;

    public AndCardIntSet() {
        backingList = new TIntArrayList(16);
        backingList.sort();
    }

    public static AndCardIntSet of(int... list) {
        var set = new TIntHashSet(list);
        TIntArrayList lst = new TIntArrayList(set);
        lst.sort();

        return new AndCardIntSet(lst);
    }

    public static AndCardIntSet of(RoaringBitmap bmap) {

        TIntArrayList lst = new TIntArrayList(bmap.getCardinality());

        lst.addAll(bmap.toArray());

        return new AndCardIntSet(lst);
    }


    private AndCardIntSet(TIntArrayList list) {
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

    public static int andCardinality(AndCardIntSet a, AndCardIntSet b) {

        if (!testHash(a,b)) {
            return 0;
        }
//
//        if (a.getCardinality() + b.getCardinality() < 10) {
//            return andLinearSmall(a, b);
//        }

        return andLinear(a,b);
    }

    private static int andLinearSmall(AndCardIntSet a, AndCardIntSet b) {
        int sum = 0;
        for (int i = 0; i < a.getCardinality(); i++) {
            for (int j = 0; j < b.getCardinality(); j++) {
                if (a.backingList.getQuick(i) == b.backingList.getQuick(j))
                    sum++;
            }
        }
        return sum;
    }

    private static int andLinear(AndCardIntSet a, AndCardIntSet b) {

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

    private static boolean testHash(AndCardIntSet a, AndCardIntSet b) {
        return (a.hash & b.hash) != 0;
    }

    public boolean cardinalityExceeds(int val) {
        return getCardinality() >= val;
    }

    public static AndCardIntSet and(AndCardIntSet a, AndCardIntSet b) {
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

        return new AndCardIntSet(andVals);
    }

    public static double weightedProduct(float[] weights, AndCardIntSet a, AndCardIntSet b) {
        int i = 0;
        int j = 0;

        double sum = 0;

        if (a.getCardinality() + b.getCardinality() < 10) {
            return weightedProductSmall(weights, a, b);
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


    private static double weightedProductSmall(float[] weights, AndCardIntSet a, AndCardIntSet b) {
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
