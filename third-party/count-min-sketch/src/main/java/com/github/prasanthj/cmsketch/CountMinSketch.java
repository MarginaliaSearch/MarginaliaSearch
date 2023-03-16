/**
 *   Copyright 2014 Prasanth Jayachandran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.prasanthj.cmsketch;

import java.nio.ByteBuffer;

/**
 * Count Min sketch is a probabilistic data structure for finding the frequency of events in a
 * stream of data. The data structure accepts two parameters epsilon and delta, epsilon specifies
 * the error in estimation and delta specifies the probability that the estimation is wrong (or the
 * confidence interval). The default values are 1% estimation error (epsilon) and 99% confidence
 * (1 - delta). Tuning these parameters results in increase or decrease in the size of the count
 * min sketch. The constructor also accepts width and depth parameters. The relationship between
 * width and epsilon (error) is width = Math.ceil(Math.exp(1.0)/epsilon). In simpler terms, the
 * lesser the error is, the greater is the width and hence the size of count min sketch.
 * The relationship between delta and depth is depth = Math.ceil(Math.log(1.0/delta)). In simpler
 * terms, the more the depth of the greater is the confidence.
 * The way it works is, if we need to estimate the number of times a certain key is inserted (or appeared in
 * the stream), count min sketch uses pairwise independent hash functions to map the key to
 * different locations in count min sketch and increment the counter.
 * <p/>
 * For example, if width = 10 and depth = 4, lets assume the hashcodes
 * for key "HELLO" using pairwise independent hash functions are 9812121, 6565512, 21312312, 8787008
 * respectively. Then the counter in hashcode % width locations are incremented.
 * <p/>
 * 0   1   2   3   4   5   6   7   8   9
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * <p/>
 * Now for a different key "WORLD", let the hashcodes be 23123123, 45354352, 8567453, 12312312.
 * As we can see below there is a collision for 2nd hashcode
 * <p/>
 * 0   1   2   3   4   5   6   7   8   9
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * --- --- --- --- --- --- --- --- --- ---
 * | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 0 |
 * --- --- --- --- --- --- --- --- --- ---
 * <p/>
 * Now, to get the estimated count for key "HELLO", same process is repeated again to find the
 * values in each position and the estimated count will be the minimum of all values (to account for
 * hash collisions).
 * <p/>
 * estimatedCount("HELLO") = min(1, 2, 1, 1)
 * <p/>
 * so even if there are multiple hash collisions, the returned value will be the best estimate
 * (upper bound) for the given key. The actual count can never be greater than this value.
 */
public class CountMinSketch {
    // 1% estimation error with 1% probability (99% confidence) that the estimation breaks this limit
    private static final float DEFAULT_DELTA = 0.01f;
    private static final float DEFAULT_EPSILON = 0.01f;
    private final int w;
    private final int d;
    private final int[][] multiset;

    public CountMinSketch() {
        this(DEFAULT_DELTA, DEFAULT_EPSILON);
    }

    public CountMinSketch(float delta, float epsilon) {
        this.w = (int) Math.ceil(Math.exp(1.0) / epsilon);
        this.d = (int) Math.ceil(Math.log(1.0 / delta));
        this.multiset = new int[d][w];
    }

    public CountMinSketch(int width, int depth) {
        this.w = width;
        this.d = depth;
        this.multiset = new int[d][w];
    }

    private CountMinSketch(int width, int depth, int[][] ms) {
        this.w = width;
        this.d = depth;
        this.multiset = ms;
    }

    public int getWidth() {
        return w;
    }

    public int getDepth() {
        return d;
    }

    /**
     * Returns the size in bytes after serialization.
     *
     * @return serialized size in bytes
     */
    public long getSizeInBytes() {
        return ((w * d) + 2) * (Integer.SIZE / 8);
    }

    public void set(byte[] key) {
        // We use the trick mentioned in "Less Hashing, Same Performance: Building a Better Bloom Filter"
        // by Kirsch et.al. From abstract 'only two hash functions are necessary to effectively
        // implement a Bloom filter without any loss in the asymptotic false positive probability'
        // The paper also proves that the same technique (using just 2 pairwise independent hash functions)
        // can be used for Count-Min sketch.

        // Lets split up 64-bit hashcode into two 32-bit hashcodes and employ the technique mentioned
        // in the above paper
        long hash64 = Murmur3.hash64(key);
        int hash1 = (int) hash64;
        int hash2 = (int) (hash64 >>> 32);
        for (int i = 1; i <= d; i++) {
            int combinedHash = hash1 + (i * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            int pos = combinedHash % w;
            multiset[i - 1][pos] += 1;
        }
    }

    public void setString(String val) {
        set(val.getBytes());
    }

    public void setByte(byte val) {
        set(new byte[]{val});
    }

    public void setInt(int val) {
        // puts int in little endian order
        set(intToByteArrayLE(val));
    }


    public void setLong(long val) {
        // puts long in little endian order
        set(longToByteArrayLE(val));
    }

    public void setFloat(float val) {
        setInt(Float.floatToIntBits(val));
    }

    public void setDouble(double val) {
        setLong(Double.doubleToLongBits(val));
    }

    private static byte[] intToByteArrayLE(int val) {
        return new byte[]{(byte) (val >> 0),
                (byte) (val >> 8),
                (byte) (val >> 16),
                (byte) (val >> 24)};
    }

    private static byte[] longToByteArrayLE(long val) {
        return new byte[]{(byte) (val >> 0),
                (byte) (val >> 8),
                (byte) (val >> 16),
                (byte) (val >> 24),
                (byte) (val >> 32),
                (byte) (val >> 40),
                (byte) (val >> 48),
                (byte) (val >> 56),};
    }

    public int getEstimatedCount(byte[] key) {
        long hash64 = Murmur3.hash64(key);
        int hash1 = (int) hash64;
        int hash2 = (int) (hash64 >>> 32);
        int min = Integer.MAX_VALUE;
        for (int i = 1; i <= d; i++) {
            int combinedHash = hash1 + (i * hash2);
            // hashcode should be positive, flip all the bits if it's negative
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            int pos = combinedHash % w;
            min = Math.min(min, multiset[i - 1][pos]);
        }

        return min;
    }

    public int getEstimatedCountString(String val) {
        return getEstimatedCount(val.getBytes());
    }

    public int getEstimatedCountByte(byte val) {
        return getEstimatedCount(new byte[]{val});
    }

    public int getEstimatedCountInt(int val) {
        return getEstimatedCount(intToByteArrayLE(val));
    }

    public int getEstimatedCountLong(long val) {
        return getEstimatedCount(longToByteArrayLE(val));
    }

    public int getEstimatedCountFloat(float val) {
        return getEstimatedCountInt(Float.floatToIntBits(val));
    }

    public int getEstimatedCountDouble(double val) {
        return getEstimatedCountLong(Double.doubleToLongBits(val));
    }

    /**
     * Merge the give count min sketch with current one. Merge will throw RuntimeException if the
     * provided CountMinSketch is not compatible with current one.
     *
     * @param that - the one to be merged
     */
    public void merge(CountMinSketch that) {
        if (that == null) {
            return;
        }

        if (this.w != that.w) {
            throw new RuntimeException("Merge failed! Width of count min sketch do not match!" +
                    "this.width: " + this.getWidth() + " that.width: " + that.getWidth());
        }

        if (this.d != that.d) {
            throw new RuntimeException("Merge failed! Depth of count min sketch do not match!" +
                    "this.depth: " + this.getDepth() + " that.depth: " + that.getDepth());
        }

        for (int i = 0; i < d; i++) {
            for (int j = 0; j < w; j++) {
                this.multiset[i][j] += that.multiset[i][j];
            }
        }
    }

    /**
     * Serialize the count min sketch to byte array. The format of serialization is width followed by
     * depth followed by integers in multiset from row1, row2 and so on..
     *
     * @return serialized byte array
     */
    public static byte[] serialize(CountMinSketch cms) {
        long serializedSize = cms.getSizeInBytes();
        ByteBuffer bb = ByteBuffer.allocate((int) serializedSize);
        bb.putInt(cms.getWidth());
        bb.putInt(cms.getDepth());
        for (int i = 0; i < cms.getDepth(); i++) {
            for (int j = 0; j < cms.getWidth(); j++) {
                bb.putInt(cms.multiset[i][j]);
            }
        }
        bb.flip();
        return bb.array();
    }

    /**
     * Deserialize the serialized count min sketch.
     *
     * @param serialized - serialized count min sketch
     * @return deserialized count min sketch object
     */
    public static CountMinSketch deserialize(byte[] serialized) {
        ByteBuffer bb = ByteBuffer.allocate(serialized.length);
        bb.put(serialized);
        bb.flip();
        int width = bb.getInt();
        int depth = bb.getInt();
        int[][] multiset = new int[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                multiset[i][j] = bb.getInt();
            }
        }
        CountMinSketch cms = new CountMinSketch(width, depth, multiset);
        return cms;
    }
}
