package nu.marginalia.index.model;

import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.hash.MurmurHash3_128;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchTermsUtil {

    /** Extract all include-terms from the specified subqueries,
     * and a return a map of the terms and their termIds.
     */
    public static Map<String, Long> getAllIncludeTerms(List<SearchSubquery> subqueries) {
        Map<String, Long> ret = new HashMap<>();

        for (var subquery : subqueries) {
            for (var include : subquery.searchTermsInclude) {
                ret.computeIfAbsent(include, i -> getWordId(include));
            }
        }

        return ret;
    }

    private static final MurmurHash3_128 hasher = new MurmurHash3_128();

    /** Translate the word to a unique id. */
    public static long getWordId(String s) {
        return hasher.hashKeyword(s);
    }
}
