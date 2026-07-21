package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.reverse.IndexLanguageContext;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.language.keywords.KeywordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static nu.marginalia.api.searchquery.IndexProtobufCodec.convertSpecLimit;

public class UnrankedSearchContext {
    private static final Logger logger = LoggerFactory.getLogger(UnrankedSearchContext.class);

    public final IndexSearchBudget budget;

    public final int limitTotal;

    public final LongList termIdsRequire;
    public final LongList termIdsRequireUnique;
    public final LongList termIdsExcludes;

    public final IndexLanguageContext languageContext;

    public final Long2ObjectOpenHashMap<String> termIdToString;
    public final IntList mandatoryDomainIds;
    public final IntList excludedDomainIds;

    public final long afterCombinedDocId;


    public static UnrankedSearchContext create(CombinedIndexReader currentIndex,
                                               KeywordHasher keywordHasher,
                                               RpcIndexUnrankedQuery request) {

        var limits = request.getQueryLimits();

        return new UnrankedSearchContext(
                keywordHasher,
                request.getLangIsoCode(),
                currentIndex,
                request.getTermsRequiredList(),
                request.getTermsExcludedList(),
                request.getExcludedDomainIdsList(),
                request.getRequiredDomainIdsList(),
                request.getAfterId(),
                limits);
    }

    public UnrankedSearchContext(
            KeywordHasher keywordHasher,
            String langIsoCode,
            CombinedIndexReader currentIndex,
            List<String> termsRequired,
            List<String> termsExcluded,
            List<Integer> excludedDomainIdsList,
            List<Integer> mandatoryDomainIdsList,
            long afterCombinedDocId,
            RpcQueryLimits limits)
    {
        this.languageContext = currentIndex.createLanguageContext(langIsoCode);

        this.budget = new IndexSearchBudget(Math.max(limits.getTimeoutMs()/2, limits.getTimeoutMs()-10));

        this.excludedDomainIds = new IntArrayList(excludedDomainIdsList);
        this.mandatoryDomainIds = new IntArrayList(mandatoryDomainIdsList);

        this.afterCombinedDocId = afterCombinedDocId;
        this.limitTotal = limits.getResultsTotal();

        this.termIdsExcludes = new LongArrayList();
        this.termIdsRequire = new LongArrayList();

        for (var word : termsRequired) {
            termIdsRequire.add(keywordHasher.hashKeyword(word));
        }

        for (var word : termsExcluded) {
            termIdsExcludes.add(keywordHasher.hashKeyword(word));
        }

        LongArrayList termIdsList = new LongArrayList();
        termIdToString = new Long2ObjectOpenHashMap<>();

        for (String term : termsRequired) {
            long id = keywordHasher.hashKeyword(term);
            termIdsList.add(id);
            termIdToString.put(id, term);
        }

        for (var term : termsExcluded) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdToString.put(id, term);
        }

        termIdsRequireUnique = new LongArrayList(new LongOpenHashSet(termIdsList));
        termIdsRequireUnique.sort(Long::compareTo);
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        LongList list = new LongArrayList(termIdsRequireUnique);
        list.sort(comparator);
        return list.toLongArray();
    }

}
