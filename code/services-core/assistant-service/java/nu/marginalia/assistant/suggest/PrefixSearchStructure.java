package nu.marginalia.assistant.suggest;

import gnu.trove.list.array.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Unhinged data structure for fast prefix searching.
 */
public class PrefixSearchStructure {
    // Core data structures
    private final HashMap<String, TIntArrayList> prefixIndex;     // Short prefix index (up to 8 chars)
    private final HashMap<String, TIntArrayList> longPrefixIndex; // Long prefix index (9-16 chars)
    private final ArrayList<String> words;                        // All words by ID
    private final TIntArrayList wordScores;                       // Scores for all words

    // Configuration
    private static final int SHORT_PREFIX_LENGTH = 8;
    private static final int MAX_INDEXED_PREFIX_LENGTH = 16;

    public int size() {
        return words.size();
    }

    // For sorting efficiency
    private static class WordScorePair {
        final String word;
        final int score;

        WordScorePair(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }

    /**
     * Creates a new PrefixTrie for typeahead search.
     */
    public PrefixSearchStructure() {
        prefixIndex = new HashMap<>(1024);
        longPrefixIndex = new HashMap<>(1024);
        words = new ArrayList<>(1024);
        wordScores = new TIntArrayList(1024);
    }

    /**
     * Adds a prefix to the index.
     */
    private void indexPrefix(String word, int wordId) {
        // Index short prefixes
        for (int i = 1; i <= Math.min(word.length(), SHORT_PREFIX_LENGTH); i++) {
            String prefix = word.substring(0, i);
            TIntArrayList wordIds = prefixIndex.computeIfAbsent(
                    prefix, k -> new TIntArrayList(16));
            wordIds.add(wordId);
        }

        // Index longer prefixes
        for (int i = SHORT_PREFIX_LENGTH + 1; i <= Math.min(word.length(), MAX_INDEXED_PREFIX_LENGTH); i++) {
            String prefix = word.substring(0, i);
            TIntArrayList wordIds = longPrefixIndex.computeIfAbsent(
                    prefix, k -> new TIntArrayList(8));
            wordIds.add(wordId);
        }

        // If the word contains spaces, also index by each term for multi-word queries
        if (word.contains(" ")) {
            String[] terms = word.split("\\s+");
            for (String term : terms) {
                if (term.length() >= 2) {
                    for (int i = 1; i <= Math.min(term.length(), SHORT_PREFIX_LENGTH); i++) {
                        String termPrefix = "t:" + term.substring(0, i);
                        TIntArrayList wordIds = prefixIndex.computeIfAbsent(
                                termPrefix, k -> new TIntArrayList(16));
                        wordIds.add(wordId);
                    }
                }
            }
        }
    }

    /**
     * Inserts a word with its associated score.
     */
    public void insert(String word, int score) {
        if (word == null || word.isEmpty()) {
            return;
        }

        // Add to the word list and index
        int wordId = words.size();
        words.add(word);
        wordScores.add(score);
        indexPrefix(word, wordId);
    }

    /**
     * Returns the top k completions for a given prefix.
     */
    public List<ScoredSuggestion> getTopCompletions(String prefix, int k) {
        if (prefix == null || prefix.isEmpty()) {
            // Return top k words by score
            return getTopKWords(k);
        }

        // Check if this is a term search (t:) - for searching within multi-word items
        boolean isTermSearch = false;
        if (prefix.startsWith("t:") && prefix.length() > 2) {
            isTermSearch = true;
            prefix = prefix.substring(2);
        }

        // 1. Fast path for short prefixes
        if (prefix.length() <= SHORT_PREFIX_LENGTH) {
            String lookupPrefix = isTermSearch ? "t:" + prefix : prefix;
            TIntArrayList wordIds = prefixIndex.get(lookupPrefix);
            if (wordIds != null) {
                return getTopKFromWordIds(wordIds, k);
            }
        }

        // 2. Fast path for long prefixes (truncate to MAX_INDEXED_PREFIX_LENGTH)
        if (prefix.length() > SHORT_PREFIX_LENGTH) {
            // Try exact match in longPrefixIndex first
            if (prefix.length() <= MAX_INDEXED_PREFIX_LENGTH) {
                TIntArrayList wordIds = longPrefixIndex.get(prefix);
                if (wordIds != null) {
                    return getTopKFromWordIds(wordIds, k);
                }
            }

            // If prefix is longer than MAX_INDEXED_PREFIX_LENGTH, truncate and filter
            if (prefix.length() > MAX_INDEXED_PREFIX_LENGTH) {
                String truncatedPrefix = prefix.substring(0, MAX_INDEXED_PREFIX_LENGTH);
                TIntArrayList candidateIds = longPrefixIndex.get(truncatedPrefix);
                if (candidateIds != null) {
                    // Filter candidates by the full prefix
                    return getFilteredTopKFromWordIds(candidateIds, prefix, k);
                }
            }
        }

        // 3. Optimized fallback for long prefixes - use prefix tree for segments
        List<ScoredSuggestion> results = new ArrayList<>();

        // Handle multi-segment queries by finding candidates from first 8 chars
        if (prefix.length() > SHORT_PREFIX_LENGTH) {
            String shortPrefix = prefix.substring(0, Math.min(prefix.length(), SHORT_PREFIX_LENGTH));
            TIntArrayList candidates = prefixIndex.get(shortPrefix);

            if (candidates != null) {
                return getFilteredTopKFromWordIds(candidates, prefix, k);
            }
        }

        // 4. Last resort - optimized binary search in sorted segments
        return findByBinarySearchPrefix(prefix, k);
    }

    /**
     * Helper to get the top k words by score.
     */
    private List<ScoredSuggestion> getTopKWords(int k) {
        // Create pairs of (score, wordId)
        int[][] pairs = new int[words.size()][2];
        for (int i = 0; i < words.size(); i++) {
            pairs[i][0] = wordScores.get(i);
            pairs[i][1] = i;
        }

        // Sort by score (descending)
        Arrays.sort(pairs, (a, b) -> Integer.compare(b[0], a[0]));

        // Take top k
        List<ScoredSuggestion> results = new ArrayList<>();
        for (int i = 0; i < Math.min(k, pairs.length); i++) {
            String word = words.get(pairs[i][1]);
            int score = pairs[i][0];
            results.add(new ScoredSuggestion(word, score));
        }

        return results;
    }

    /**
     * Helper to get the top k words from a list of word IDs.
     */
    private List<ScoredSuggestion> getTopKFromWordIds(TIntArrayList wordIds, int k) {
        if (wordIds == null || wordIds.isEmpty()) {
            return Collections.emptyList();
        }

        // For small lists, avoid sorting
        if (wordIds.size() <= k) {
            List<ScoredSuggestion> results = new ArrayList<>(wordIds.size());
            int[] ids = wordIds.toArray();
            for (int wordId : ids) {
                if (wordId >= 0 && wordId < words.size()) {
                    results.add(new ScoredSuggestion(words.get(wordId), wordScores.get(wordId)));
                }
            }
            results.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
            return results;
        }

        // For larger lists, use an array-based approach for better performance
        // Find top k without full sorting
        int[] topScores = new int[k];
        int[] topWordIds = new int[k];
        int[] ids = wordIds.toArray();

        // Initialize with first k elements
        int filledCount = Math.min(k, ids.length);
        for (int i = 0; i < filledCount; i++) {
            int wordId = ids[i];
            if (wordId >= 0 && wordId < words.size()) {
                topWordIds[i] = wordId;
                topScores[i] = wordScores.get(wordId);
            }
        }

        // Sort initial elements
        for (int i = 0; i < filledCount; i++) {
            for (int j = i + 1; j < filledCount; j++) {
                if (topScores[j] > topScores[i]) {
                    // Swap scores
                    int tempScore = topScores[i];
                    topScores[i] = topScores[j];
                    topScores[j] = tempScore;

                    // Swap word IDs
                    int tempId = topWordIds[i];
                    topWordIds[i] = topWordIds[j];
                    topWordIds[j] = tempId;
                }
            }
        }

        // Process remaining elements
        int minScore = filledCount > 0 ? topScores[filledCount - 1] : Integer.MIN_VALUE;

        for (int i = k; i < ids.length; i++) {
            int wordId = ids[i];
            if (wordId >= 0 && wordId < words.size()) {
                int score = wordScores.get(wordId);

                if (score > minScore) {
                    // Replace the lowest element
                    topScores[filledCount - 1] = score;
                    topWordIds[filledCount - 1] = wordId;

                    // Bubble up the new element
                    for (int j = filledCount - 1; j > 0; j--) {
                        if (topScores[j] > topScores[j - 1]) {
                            // Swap scores
                            int tempScore = topScores[j];
                            topScores[j] = topScores[j - 1];
                            topScores[j - 1] = tempScore;

                            // Swap word IDs
                            int tempId = topWordIds[j];
                            topWordIds[j] = topWordIds[j - 1];
                            topWordIds[j - 1] = tempId;
                        } else {
                            break;
                        }
                    }

                    // Update min score
                    minScore = topScores[filledCount - 1];
                }
            }
        }

        // Create result list
        List<ScoredSuggestion> results = new ArrayList<>(filledCount);
        for (int i = 0; i < filledCount; i++) {
            results.add(new ScoredSuggestion(words.get(topWordIds[i]), topScores[i]));
        }

        return results;
    }

    /**
     * Use binary search on sorted word segments to efficiently find matches.
     */
    private List<ScoredSuggestion> findByBinarySearchPrefix(String prefix, int k) {
        // If we have a lot of words, use an optimized segment approach
        if (words.size() > 1000) {
            // Divide words into segments for better locality
            int segmentSize = 1000;
            int numSegments = (words.size() + segmentSize - 1) / segmentSize;

            // Find matches using binary search within each segment
            List<WordScorePair> allMatches = new ArrayList<>();
            for (int segment = 0; segment < numSegments; segment++) {
                int start = segment * segmentSize;
                int end = Math.min(start + segmentSize, words.size());

                // Binary search for first potential match
                int pos = Collections.binarySearch(
                        words.subList(start, end),
                        prefix,
                        (a, b) -> a.compareTo(b)
                );

                if (pos < 0) {
                    pos = -pos - 1;
                }

                // Collect all matches
                for (int i = start + pos; i < end && i < words.size(); i++) {
                    String word = words.get(i);
                    if (word.startsWith(prefix)) {
                        allMatches.add(new WordScorePair(word, wordScores.get(i)));
                    } else if (word.compareTo(prefix) > 0) {
                        break; // Past potential matches
                    }
                }
            }

            // Sort by score and take top k
            allMatches.sort((a, b) -> Integer.compare(b.score, a.score));
            List<ScoredSuggestion> results = new ArrayList<>(Math.min(k, allMatches.size()));
            for (int i = 0; i < Math.min(k, allMatches.size()); i++) {
                WordScorePair pair = allMatches.get(i);
                results.add(new ScoredSuggestion(pair.word, pair.score));
            }
            return results;
        }

        // Fallback for small dictionaries - linear scan but optimized
        return simpleSearchFallback(prefix, k);
    }

    /**
     * Optimized linear scan - only used for small dictionaries.
     */
    private List<ScoredSuggestion> simpleSearchFallback(String prefix, int k) {
        // Use primitive arrays for better cache locality
        int[] matchScores = new int[Math.min(words.size(), 100)]; // Assume we won't find more than 100 matches
        String[] matchWords = new String[matchScores.length];
        int matchCount = 0;

        for (int i = 0; i < words.size() && matchCount < matchScores.length; i++) {
            String word = words.get(i);
            if (word.startsWith(prefix)) {
                matchWords[matchCount] = word;
                matchScores[matchCount] = wordScores.get(i);
                matchCount++;
            }
        }

        // Sort matches by score (in-place for small arrays)
        for (int i = 0; i < matchCount; i++) {
            for (int j = i + 1; j < matchCount; j++) {
                if (matchScores[j] > matchScores[i]) {
                    // Swap scores
                    int tempScore = matchScores[i];
                    matchScores[i] = matchScores[j];
                    matchScores[j] = tempScore;

                    // Swap words
                    String tempWord = matchWords[i];
                    matchWords[i] = matchWords[j];
                    matchWords[j] = tempWord;
                }
            }
        }

        // Create results
        List<ScoredSuggestion> results = new ArrayList<>(Math.min(k, matchCount));
        for (int i = 0; i < Math.min(k, matchCount); i++) {
            results.add(new ScoredSuggestion(matchWords[i], matchScores[i]));
        }

        return results;
    }

    /**
     * Get top k words from candidate IDs, filtering by the full prefix.
     */
    private List<ScoredSuggestion> getFilteredTopKFromWordIds(TIntArrayList wordIds, String fullPrefix, int k) {
        if (wordIds == null || wordIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Make primitive arrays for better performance
        String[] matchWords = new String[Math.min(wordIds.size(), 1000)];
        int[] matchScores = new int[matchWords.length];
        int matchCount = 0;

        int[] ids = wordIds.toArray();
        for (int i = 0; i < ids.length && matchCount < matchWords.length; i++) {
            int wordId = ids[i];
            if (wordId >= 0 && wordId < words.size()) {
                String word = words.get(wordId);
                if (word.startsWith(fullPrefix)) {
                    matchWords[matchCount] = word;
                    matchScores[matchCount] = wordScores.get(wordId);
                    matchCount++;
                }
            }
        }

        // Sort by score (efficient insertion sort for small k)
        for (int i = 0; i < Math.min(matchCount, k); i++) {
            int maxPos = i;
            for (int j = i + 1; j < matchCount; j++) {
                if (matchScores[j] > matchScores[maxPos]) {
                    maxPos = j;
                }
            }
            if (maxPos != i) {
                // Swap
                int tempScore = matchScores[i];
                matchScores[i] = matchScores[maxPos];
                matchScores[maxPos] = tempScore;

                String tempWord = matchWords[i];
                matchWords[i] = matchWords[maxPos];
                matchWords[maxPos] = tempWord;
            }
        }

        // Create result list (only up to k elements)
        List<ScoredSuggestion> results = new ArrayList<>(Math.min(k, matchCount));
        for (int i = 0; i < Math.min(k, matchCount); i++) {
            results.add(new ScoredSuggestion(matchWords[i], matchScores[i]));
        }

        return results;
    }

    /**
     * Class representing a suggested completion.
     */
    public static class ScoredSuggestion implements Comparable<ScoredSuggestion> {
        private final String word;
        private final int score;

        public ScoredSuggestion(String word, int score) {
            this.word = word;
            this.score = score;
        }

        public String getWord() {
            return word;
        }

        public int getScore() {
            return score;
        }

        @Override
        public String toString() {
            return word + " (" + score + ")";
        }

        @Override
        public int compareTo(@NotNull PrefixSearchStructure.ScoredSuggestion o) {
            return Integer.compare(this.score, o.score);
        }
    }
}