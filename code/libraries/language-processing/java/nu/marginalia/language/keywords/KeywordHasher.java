package nu.marginalia.language.keywords;

import nu.marginalia.hash.MurmurHash3_128;

public sealed interface KeywordHasher {
    MurmurHash3_128 hasher = new MurmurHash3_128();

    long hashKeyword(String keyword);

    /** Hash algorithm that seeds a Murmur128 algorithm with Java's string hashCode(), but
     * then only looks at 7 bit ASCII for the Murmur calculations.  This works well for English
     * and similar languages, but falls apart completely for languages that are not dominated by
     * the 7 bit ASCII subset.
     */
    final class AsciiIsh implements KeywordHasher {
        public long hashKeyword(String keyword) {
            return hasher.hashNearlyASCII(keyword);
        }
    }

    /** Hash algorithm that is based on Murmur128 folded over on itself to make a 64 bit key */
    final class Utf8 implements KeywordHasher {
        public long hashKeyword(String keyword) {
            return hasher.hashUtf8(keyword);
        }
    }
}
