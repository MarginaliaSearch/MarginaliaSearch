package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.reverse.IndexLanguageContext;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.language.keywords.KeywordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static nu.marginalia.api.searchquery.IndexProtobufCodec.convertSpecLimit;

public class SearchContext {
    private static final Logger logger = LoggerFactory.getLogger(SearchContext.class);

    public final IndexSearchBudget budget;

    public final int fetchSize;
    public final int limitByDomain;
    public final int limitTotal;

    private final int docCount;

    public final RpcResultRankingParameters params;
    public final RpcQueryTerms queryTerms;
    public final QueryParams queryParams;

    public final CompiledQuery<String> compiledQuery;
    public final CompiledQueryLong compiledQueryIds;

    /** Bitmask whose position correspond to the positions in the compiled query data
     * which are regular words.
     */
    public final BitSet regularMask;

    /** Bitmask whose position correspond to the positions in the compiled query data
     * which are ngrams.
     */
    public final BitSet ngramsMask;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt fullCounts;

    /** CqDataInt associated with frequency information of the terms in the query
     * in the full index.  The dataset is indexed by the compiled query. */
    public final CqDataInt priorityCounts;

    public final TermIdList termIdsAll;
    public final PhraseConstraintGroupList phraseConstraints;

    public final LongList termIdsRequire;
    public final LongList termIdsExcludes;
    public final LongList termIdsPriority;
    public final FloatList termIdsPriorityWeights;

    public final IndexLanguageContext languageContext;

    public final Long2ObjectOpenHashMap<String> termIdToString;
    public final IntList mandatoryDomainIds;
    public final IntList excludedDomainIds;

    public static SearchContext create(CombinedIndexReader currentIndex,
                                       KeywordHasher keywordHasher,
                                       RpcIndexQuery request, SearchSet searchSet) {
        var limits = request.getQueryLimits();
        var queryTerms = request.getTerms();

        var queryParams = new QueryParams(
                request.hasQuality() ? convertSpecLimit(request.getQuality()) : SpecificationLimit.none(),
                request.hasYear() ? convertSpecLimit(request.getYear()) : SpecificationLimit.none(),
                request.hasSize() ? convertSpecLimit(request.getSize()) : SpecificationLimit.none(),
                request.hasRank() ? convertSpecLimit(request.getRank()) : SpecificationLimit.none(),
                searchSet,
                QueryStrategy.parse(request.getQueryStrategy()));

        var rankingParams = request.hasParameters() ? request.getParameters() : PrototypeRankingParameters.sensibleDefaults();

        return new SearchContext(
                keywordHasher,
                request.getLangIsoCode(),
                currentIndex,
                queryTerms.getCompiledQuery(),
                queryParams,
                queryTerms,
                rankingParams,
                request.getExcludedDomainIdsList(),
                limits);
    }

    public SearchContext(
            KeywordHasher keywordHasher,
            String langIsoCode,
            CombinedIndexReader currentIndex,
            String queryExpression,
            QueryParams queryParams,
            RpcQueryTerms query,
            RpcResultRankingParameters rankingParams,
            List<Integer> excludedDomainIdsList,
            RpcQueryLimits limits)
    {
        this.docCount = currentIndex.totalDocCount();
        this.languageContext = currentIndex.createLanguageContext(langIsoCode);

        this.budget = new IndexSearchBudget(Math.max(limits.getTimeoutMs()/2, limits.getTimeoutMs()-50));
        this.queryTerms = query;
        this.params = rankingParams;
        this.queryParams = queryParams;
        this.mandatoryDomainIds = queryParams.searchSet().domainIds();
        this.excludedDomainIds = new IntArrayList(excludedDomainIdsList);

        this.fetchSize = limits.getFetchSize();
        this.limitByDomain = limits.getResultsByDomain();
        this.limitTotal = limits.getResultsTotal();

        this.compiledQuery = CompiledQueryParser.parse(queryExpression);
        this.compiledQueryIds = compiledQuery.mapToLong(keywordHasher::hashKeyword);
        int[] full = new int[compiledQueryIds.size()];
        int[] prio = new int[compiledQueryIds.size()];

        this.ngramsMask = new BitSet(compiledQuery.size());
        this.regularMask = new BitSet(compiledQuery.size());

        for (int idx = 0; idx < compiledQueryIds.size(); idx++) {
            long id = compiledQueryIds.at(idx);
            full[idx] = currentIndex.numHits(this.languageContext, id);
            prio[idx] = currentIndex.numHitsPrio(this.languageContext, id);

            if (compiledQuery.at(idx).contains("_")) {
                ngramsMask.set(idx);
            }
            else {
                regularMask.set(idx);
            }
        }

        this.fullCounts = new CqDataInt(full);
        this.priorityCounts = new CqDataInt(prio);

        this.termIdsExcludes = new LongArrayList();
        this.termIdsPriority = new LongArrayList();
        this.termIdsPriorityWeights = new FloatArrayList(queryTerms.getTermsPriorityWeightList());
        this.termIdsRequire = new LongArrayList();

        for (var word : queryTerms.getTermsRequireList()) {
            termIdsRequire.add(keywordHasher.hashKeyword(word));
        }

        for (var word : queryTerms.getTermsExcludeList()) {
            termIdsExcludes.add(keywordHasher.hashKeyword(word));
        }

        for (var word : queryTerms.getTermsPriorityList()) {
            termIdsPriority.add(keywordHasher.hashKeyword(word));
        }

        LongArrayList termIdsList = new LongArrayList();
        termIdToString = new Long2ObjectOpenHashMap<>();

        for (String term : compiledQuery) {
            long id = keywordHasher.hashKeyword(term);
            termIdsList.add(id);
            termIdToString.put(id, term);
        }

        for (var term : queryTerms.getTermsPriorityList()) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdToString.put(id, term);
        }

        for (var term : queryTerms.getTermsRequireList()) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdToString.put(id, term);
        }

        for (var term : queryTerms.getTermsExcludeList()) {
            long id = keywordHasher.hashKeyword(term);
            if (termIdToString.containsKey(id))
                continue;
            termIdToString.put(id, term);
        }

        termIdsAll = new TermIdList(termIdsList);

        var constraintsMandatory = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsFull = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsOptional = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();

        for (var definition : queryTerms.getPhrasesList()) {
            var constraint = new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, definition.getTermsList(), termIdsAll);

            switch (definition.getType()) {
                case FULL -> constraintsFull.add(constraint);
                case MANDATORY -> constraintsMandatory.add(constraint);
                case OPTIONAL -> constraintsOptional.add(constraint);
            }
        }

        if (constraintsFull.isEmpty()) {
            logger.warn("No full constraints in query, adding empty group");
            constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(keywordHasher, queryTerms.getTermsRequireList(), termIdsAll));
        }

        this.phraseConstraints = new PhraseConstraintGroupList(constraintsFull.getFirst(), constraintsMandatory, constraintsOptional);
    }

    public int termFreqDocCount() {
        return docCount;
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        LongList list = new LongArrayList(compiledQueryIds.copyData());
        list.sort(comparator);
        return list.toLongArray();
    }

}
