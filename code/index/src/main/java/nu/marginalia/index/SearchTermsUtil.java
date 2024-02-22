package nu.marginalia.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.index.model.IndexSearchTerms;
import nu.marginalia.hash.MurmurHash3_128;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchTermsUtil {

    /** Extract the search terms from the specified subquery. */
    public static IndexSearchTerms extractSearchTerms(SearchSubquery request) {
        final LongList excludes = new LongArrayList();
        final LongList includes = new LongArrayList();
        final LongList priority = new LongArrayList();
        final List<LongList> coherences = new ArrayList<>();

        if (!addEachTerm(includes, request.searchTermsInclude)) {
            return new IndexSearchTerms();
        }

        //                  This looks like a bug, but it's not
        //                    v---                ----v
        if (!addEachTerm(includes, request.searchTermsAdvice)) {
            return new IndexSearchTerms();
        }

        for (var coherence : request.searchTermCoherences) {
            LongList parts = new LongArrayList(coherence.size());

            if (!addEachTerm(parts, coherence)) {
                return new IndexSearchTerms();
            }

            coherences.add(parts);
        }

        // we don't care if we can't find these:
        addEachNonMandatoryTerm(excludes, request.searchTermsExclude);
        addEachNonMandatoryTerm(priority, request.searchTermsPriority);

        return new IndexSearchTerms(includes, excludes, priority, coherences);
    }

    private static boolean addEachTerm(LongList ret, List<String> words) {
        boolean success = true;

        for (var word : words) {
            ret.add(getWordId(word));
        }

        return success;
    }

    private static void addEachNonMandatoryTerm(LongList ret, List<String> words) {
        for (var word : words) {
            ret.add(getWordId(word));
        }
    }


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
