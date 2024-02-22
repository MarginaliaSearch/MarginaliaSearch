package nu.marginalia.index.svc;

import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SearchTermsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SearchIndexSearchTerms getSearchTerms(SearchSubquery request) {
        final LongList excludes = new LongArrayList();
        final LongList includes = new LongArrayList();
        final LongList priority = new LongArrayList();
        final List<LongList> coherences = new ArrayList<>();

        if (!addEachTerm(includes, request.searchTermsInclude)) {
            return new SearchIndexSearchTerms();
        }

        //                  This looks like a bug, but it's not
        //                    v---                ----v
        if (!addEachTerm(includes, request.searchTermsAdvice)) {
            return new SearchIndexSearchTerms();
        }

        for (var coherence : request.searchTermCoherences) {
            LongList parts = new LongArrayList(coherence.size());

            if (!addEachTerm(parts, coherence)) {
                return new SearchIndexSearchTerms();
            }

            coherences.add(parts);
        }

        // we don't care if we can't find these:
        addEachNonMandatoryTerm(excludes, request.searchTermsExclude);
        addEachNonMandatoryTerm(priority, request.searchTermsPriority);

        return new SearchIndexSearchTerms(includes, excludes, priority, coherences);
    }

    private boolean addEachTerm(LongList ret, List<String> words) {
        boolean success = true;

        for (var word : words) {
            ret.add(getWordId(word));
        }

        return success;
    }

    private void addEachNonMandatoryTerm(LongList ret, List<String> words) {
        for (var word : words) {
            ret.add(getWordId(word));
        }
    }


    public Map<String, Long> getAllIncludeTerms(List<SearchSubquery> subqueries) {
        Map<String, Long> ret = new HashMap<>();

        for (var subquery : subqueries) {
            for (var include : subquery.searchTermsInclude) {
                ret.computeIfAbsent(include, i -> getWordId(include));
            }
        }

        return ret;
    }

    static MurmurHash3_128 hasher = new MurmurHash3_128();
    public long getWordId(String s) {
        return hasher.hashKeyword(s);
    }
}
