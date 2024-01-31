package nu.marginalia.hash;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** A modified version of Commons Codec's murmur hash
 *  that minimizes allocations.
 * */
public class MurmurHash3_128 {
    private static final boolean NO_FLATTEN_UNICODE =
            Boolean.getBoolean("system.noFlattenUnicode");

    /**
     * A default seed to use for the murmur hash algorithm.
     * Has the value {@code 104729}.
     */
    public static final int DEFAULT_SEED = 104729;

    // Constants for 128-bit variant
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private static final int R1 = 31;
    private static final int R2 = 27;
    private static final int R3 = 33;
    private static final int M = 5;
    private static final int N1 = 0x52dce729;
    private static final int N2 = 0x38495ab5;

    /** Assumes data is ASCII, or at the very least that you only care about the lower
     * bytes of your string (which may be fine for hashing mostly latin script).
     * <p>
     * Fold the 128 bit hash into 64 bits by xor:ing msw and lsw
     */
    public long hashLowerBytes(String data) {
        return hash64(data, 0, data.length(), DEFAULT_SEED);
    }

    /** Like hashASCIIOnly except seeded with the Java String.hashCode()
     * to provide better behavior for non-ASCII strings.  It's much worse
     * than doing it properly, but better than not doing this.
     */
    public long hashNearlyASCII(String data) {
        return hash64(data, 0, data.length(), data.hashCode());
    }

    /** Select the hash function appropriate for keywords based system configuration,
     * and hash the keyword.
     */
    public long hashKeyword(String data) {
        if (NO_FLATTEN_UNICODE) {
            return hash64(data, 0, data.length(), DEFAULT_SEED);
        }
        else {
            return hashNearlyASCII(data);
        }
    }

    /** Hash the bytes; fold the 128 bit hash into 64 bits by xor:ing msw and lsw */
    public long hash(byte[] data) {
        return hash64(data, 0, data.length, DEFAULT_SEED);
    }

    private static long hash64(final CharSequence data, final int offset, final int length, final long seed) {
        long h1 = seed;
        long h2 = seed;
        final int nblocks = length >> 4;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = offset + (i << 4);
            long k1 = getLittleEndianLong(data, index);
            long k2 = getLittleEndianLong(data, index + 8);

            // mix functions for k1
            k1 *= C1;
            k1 = Long.rotateLeft(k1, R1);
            k1 *= C2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, R2);
            h1 += h2;
            h1 = h1 * M + N1;

            // mix functions for k2
            k2 *= C2;
            k2 = Long.rotateLeft(k2, R3);
            k2 *= C1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, R1);
            h2 += h1;
            h2 = h2 * M + N2;
        }

        // tail
        long k1 = 0;
        long k2 = 0;
        final int index = offset + (nblocks << 4);
        switch (offset + length - index) {
            case 15:
                k2 ^= ((long) data.charAt(index + 14) & 0xff) << 48;
            case 14:
                k2 ^= ((long) data.charAt(index + 13) & 0xff) << 40;
            case 13:
                k2 ^= ((long) data.charAt(index + 12) & 0xff) << 32;
            case 12:
                k2 ^= ((long) data.charAt(index + 11) & 0xff) << 24;
            case 11:
                k2 ^= ((long) data.charAt(index + 10) & 0xff) << 16;
            case 10:
                k2 ^= ((long) data.charAt(index + 9) & 0xff) << 8;
            case 9:
                k2 ^= data.charAt(index + 8) & 0xff;
                k2 *= C2;
                k2 = Long.rotateLeft(k2, R3);
                k2 *= C1;
                h2 ^= k2;

            case 8:
                k1 ^= ((long) data.charAt(index + 7) & 0xff) << 56;
            case 7:
                k1 ^= ((long) data.charAt(index + 6) & 0xff) << 48;
            case 6:
                k1 ^= ((long) data.charAt(index + 5) & 0xff) << 40;
            case 5:
                k1 ^= ((long) data.charAt(index + 4) & 0xff) << 32;
            case 4:
                k1 ^= ((long) data.charAt(index + 3) & 0xff) << 24;
            case 3:
                k1 ^= ((long) data.charAt(index + 2) & 0xff) << 16;
            case 2:
                k1 ^= ((long) data.charAt(index + 1) & 0xff) << 8;
            case 1:
                k1 ^= data.charAt(index) & 0xff;
                k1 *= C1;
                k1 = Long.rotateLeft(k1, R1);
                k1 *= C2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return h1^h2; // non-standard 128->64 bit transformation
    }

    private static long hash64(final byte[] data, final int offset, final int length, final long seed) {
        long h1 = seed;
        long h2 = seed;
        final int nblocks = length >> 4;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = offset + (i << 4);
            long k1 = getLittleEndianLong(data, index);
            long k2 = getLittleEndianLong(data, index + 8);

            // mix functions for k1
            k1 *= C1;
            k1 = Long.rotateLeft(k1, R1);
            k1 *= C2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, R2);
            h1 += h2;
            h1 = h1 * M + N1;

            // mix functions for k2
            k2 *= C2;
            k2 = Long.rotateLeft(k2, R3);
            k2 *= C1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, R1);
            h2 += h1;
            h2 = h2 * M + N2;
        }

        // tail
        long k1 = 0;
        long k2 = 0;
        final int index = offset + (nblocks << 4);
        switch (offset + length - index) {
            case 15:
                k2 ^= ((long) data[index + 14] & 0xff) << 48;
            case 14:
                k2 ^= ((long) data[index + 13] & 0xff) << 40;
            case 13:
                k2 ^= ((long) data[index + 12] & 0xff) << 32;
            case 12:
                k2 ^= ((long) data[index + 11] & 0xff) << 24;
            case 11:
                k2 ^= ((long) data[index + 10] & 0xff) << 16;
            case 10:
                k2 ^= ((long) data[index + 9] & 0xff) << 8;
            case 9:
                k2 ^= data[index + 8] & 0xff;
                k2 *= C2;
                k2 = Long.rotateLeft(k2, R3);
                k2 *= C1;
                h2 ^= k2;

            case 8:
                k1 ^= ((long) data[index + 7] & 0xff) << 56;
            case 7:
                k1 ^= ((long) data[index + 6] & 0xff) << 48;
            case 6:
                k1 ^= ((long) data[index + 5] & 0xff) << 40;
            case 5:
                k1 ^= ((long) data[index + 4] & 0xff) << 32;
            case 4:
                k1 ^= ((long) data[index + 3] & 0xff) << 24;
            case 3:
                k1 ^= ((long) data[index + 2] & 0xff) << 16;
            case 2:
                k1 ^= ((long) data[index + 1] & 0xff) << 8;
            case 1:
                k1 ^= data[index] & 0xff;
                k1 *= C1;
                k1 = Long.rotateLeft(k1, R1);
                k1 *= C2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return h1^h2; // non-standard 128->64 bit transformation
    }

    private static long getLittleEndianLong(final CharSequence data, final int index) {
        return (((long) data.charAt(index    ) & 0xff)      ) |
                (((long) data.charAt(index + 1) & 0xff) <<  8) |
                (((long) data.charAt(index + 2) & 0xff) << 16) |
                (((long) data.charAt(index + 3) & 0xff) << 24) |
                (((long) data.charAt(index + 4) & 0xff) << 32) |
                (((long) data.charAt(index + 5) & 0xff) << 40) |
                (((long) data.charAt(index + 6) & 0xff) << 48) |
                (((long) data.charAt(index + 7) & 0xff) << 56);
    }

    private static long getLittleEndianLong(final byte[] data, final int index) {
        return (((long) data[index    ] & 0xff)      ) |
                (((long) data[index + 1] & 0xff) <<  8) |
                (((long) data[index + 2] & 0xff) << 16) |
                (((long) data[index + 3] & 0xff) << 24) |
                (((long) data[index + 4] & 0xff) << 32) |
                (((long) data[index + 5] & 0xff) << 40) |
                (((long) data[index + 6] & 0xff) << 48) |
                (((long) data[index + 7] & 0xff) << 56);
    }
    private static long fmix64(long hash) {
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return hash;
    }

}
