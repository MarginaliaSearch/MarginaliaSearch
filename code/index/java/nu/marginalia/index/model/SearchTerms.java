package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.query.SearchQuery;

import static nu.marginalia.index.model.SearchTermsUtil.getWordId;

public final class SearchTerms {
    private final LongList advice;
    private final LongList excludes;
    private final LongList priority;

    // Create a set of stopword IDs for efficient lookup during search
    public static final LongArraySet stopWords = createStopWordsSet();
    
    private static LongArraySet createStopWordsSet() {
        LongArraySet stopWordsSet = new LongArraySet();
        
        // Add common stopwords that should be filtered out for better search relevancy
        String[] commonStopwords = {
            // Articles and determiners
            "a", "an", "the", "this", "that", "these", "those",
            
            // Interrogative words (question words) - these are key for the user's request
            "what", "when", "where", "who", "whom", "whose", "why", "how",
            
            // Common auxiliary verbs
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having",
            "do", "does", "did", "will", "would", "could", "should", "may", "might", "can",
            
            // Prepositions
            "of", "in", "on", "at", "to", "for", "with", "by", "from", "up", "about", "into",
            "through", "during", "before", "after", "above", "below", "between", "among",
            
            // Conjunctions
            "and", "or", "but", "so", "yet", "nor", "for", "as", "if", "because", "since",
            "while", "although", "though", "unless", "until", "when", "where", "why", "how",
            
            // Pronouns
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "her", "its", "our", "their", "mine", "yours", "hers", "ours", "theirs",
            
            // Other common function words
            "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "not",
            "only", "own", "same", "so", "than", "too", "very", "just", "now", "here", "there", "then"
        };
        
        for (String stopword : commonStopwords) {
            stopWordsSet.add(getWordId(stopword));
        }
        
        return stopWordsSet;
    }

    private final CompiledQueryLong compiledQueryIds;

    public SearchTerms(SearchQuery query,
                       CompiledQueryLong compiledQueryIds)
    {
        this.excludes = new LongArrayList();
        this.priority = new LongArrayList();

        this.advice = new LongArrayList();
        this.compiledQueryIds = compiledQueryIds;

        for (var word : query.searchTermsAdvice) {
            advice.add(getWordId(word));
        }

        for (var word : query.searchTermsExclude) {
            excludes.add(getWordId(word));
        }

        for (var word : query.searchTermsPriority) {
            priority.add(getWordId(word));
        }
    }

    public boolean isEmpty() {
        return compiledQueryIds.isEmpty();
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        LongList list = new LongArrayList(compiledQueryIds.copyData());
        list.sort(comparator);
        return list.toLongArray();
    }


    public LongList excludes() {
        return excludes;
    }
    public LongList advice() {
        return advice;
    }
    public LongList priority() {
        return priority;
    }

    public CompiledQueryLong compiledQuery() { return compiledQueryIds; }

}
