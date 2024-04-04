package nu.marginalia.index.model;

import nu.marginalia.hash.MurmurHash3_128;

public class SearchTermsUtil {

    private static final MurmurHash3_128 hasher = new MurmurHash3_128();

    /** Translate the word to a unique id. */
    public static long getWordId(String s) {
        return hasher.hashKeyword(s);
    }
}
