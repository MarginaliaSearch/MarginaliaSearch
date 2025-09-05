package nu.marginalia.language;

import java.util.Set;

/** Logic for deciding which words are eligible to be keywords.
 */
public class WordPatterns {
    public static final int MIN_WORD_LENGTH = 1;
    public static final int MAX_WORD_LENGTH = 64;

    public static final String WORD_TOKEN_JOINER = "_";

    // Common stopwords that should be filtered out for better search relevancy
    // This includes interrogative words, determinative words, and other common function words
    private static final Set<String> STOPWORDS = Set.of(
        // Articles and determiners
        "a", "an", "the", "this", "that", "these", "those",
        
        // Interrogative words (question words)
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
    );

    /** Run checks on the word and exclude terms with too many special characters
     */
    public static boolean isNotJunkWord(String word) {
        if (word.isBlank()) {
            return false;
        }
        if (hasMoreThanN(word, '-', 4)) {
            return false;
        }
        if (hasMoreThanN(word, '+', 2)) {
            return false;
        }
        if (word.startsWith("-")
                || word.endsWith("-")
        ) {
            return false;
        }

        int numDigits = 0;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isDigit(word.charAt(i))) {
                numDigits++;
            }
            if (numDigits > 16)
                return false;
        }

        return true;
    }

    private static boolean hasMoreThanN(String s, char c, int max) {
        int idx = 0;
        for (int i = 0; i <= max; i++) {
            idx = s.indexOf(c, idx+1);
            if (idx < 0 || idx >= s.length() - 1)
                return false;
        }
        return true;
    }

    /** Check if a word is a stopword that should be filtered out for better search relevancy.
     * This includes interrogative words, determinative words, and other common function words.
     */
    public static boolean isStopWord(String s) {
        if (s == null || s.isBlank()) {
            return true;
        }
        
        // First check if it's a junk word
        if (!isNotJunkWord(s)) {
            return true;
        }
        
        // Check if it's in our stopword list (case-insensitive)
        return STOPWORDS.contains(s.toLowerCase());
    }


}
