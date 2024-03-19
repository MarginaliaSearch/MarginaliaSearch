package nu.marginalia.segmentation;

import nu.marginalia.hash.MurmurHash3_128;

/** A group of hash functions that can be used to hash a sequence of strings,
 * that also has an inverse operation that can be used to remove a previously applied
 * string from the sequence. */
public sealed interface HasherGroup {
    /** Apply a hash to the accumulator */
    long apply(long acc, long add);

    /** Remove a hash that was added n operations ago from the accumulator, add a new one */
    long replace(long acc, long add, long rem, int n);

    /** Create a new hasher group that preserves the order of appleid hash functions */
    static HasherGroup ordered() {
        return new OrderedHasher();
    }

    /** Create a new hasher group that does not preserve the order of applied hash functions */
    static HasherGroup unordered() {
        return new UnorderedHasher();
    }

    /** Bake the words in the sentence into a hash successively using the group's apply function */
    default long rollingHash(String[] parts) {
        long code = 0;
        for (String part : parts) {
            code = apply(code, hash(part));
        }
        return code;
    }

    MurmurHash3_128 hash = new MurmurHash3_128();
    /** Calculate the hash of a string */
    static long hash(String term) {
        return hash.hashNearlyASCII(term);
    }

    final class UnorderedHasher implements HasherGroup {

        public long apply(long acc, long add) {
            return acc ^ add;
        }

        public long replace(long acc, long add, long rem, int n) {
            return acc ^ rem ^ add;
        }
    }

    final class OrderedHasher implements HasherGroup {

        public long apply(long acc, long add) {
            return Long.rotateLeft(acc, 1) ^ add;
        }

        public long replace(long acc, long add, long rem, int n) {
            return Long.rotateLeft(acc, 1) ^ add ^ Long.rotateLeft(rem, n);
        }
    }
}
