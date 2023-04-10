package nu.marginalia.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.dict.OffHeapDictionaryHashMap;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.lexicon.KeywordLexiconReadOnlyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class SearchTermsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final KeywordLexiconReadOnlyView lexicon;

    @Inject
    public SearchTermsService(KeywordLexiconReadOnlyView lexicon) {
        this.lexicon = lexicon;
    }

    public SearchIndexSearchTerms getSearchTerms(SearchSubquery request) {
        final IntList excludes = new IntArrayList();
        final IntList includes = new IntArrayList();
        final IntList priority = new IntArrayList();
        final List<IntList> coherences = new ArrayList<>();

        if (!addEachTerm(includes, request.searchTermsInclude)) {
            return new SearchIndexSearchTerms();
        }

        //                  This looks like a bug, but it's not
        //                    v---                ----v
        if (!addEachTerm(includes, request.searchTermsAdvice)) {
            return new SearchIndexSearchTerms();
        }

        for (var coherence : request.searchTermCoherences) {
            IntList parts = new IntArrayList(coherence.size());

            if (!addEachTerm(parts, coherence)) {
                return new SearchIndexSearchTerms();
            }

            coherences.add(parts);
        }

        // we don't care if we can't find these:
        addEachTerm(excludes, request.searchTermsExclude);
        addEachTerm(priority, request.searchTermsPriority);

        return new SearchIndexSearchTerms(includes, excludes, priority, coherences);
    }

    private boolean addEachTerm(IntList ret, List<String> words) {
        boolean success = true;

        for (var exclude : words) {
            var word = lookUpWord(exclude);

            if (word.isPresent()) {
                lookUpWord(exclude).ifPresent(ret::add);
            }
            else {
                success = false;
            }
        }
        return success;
    }


    public OptionalInt lookUpWord(String s) {
        int ret = lexicon.get(s);
        if (ret == OffHeapDictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

    public Map<String, Integer> getAllIncludeTerms(List<SearchSubquery> subqueries) {
        Map<String, Integer> ret = new HashMap<>();

        for (var subquery : subqueries) {
            for (var include : subquery.searchTermsInclude) {
                ret.computeIfAbsent(include, term -> lookUpWord(term).orElse(-1));
            }
        }

        return ret;
    }
}
