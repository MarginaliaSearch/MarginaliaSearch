package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.language.WordPatterns;

import java.util.ArrayList;
import java.util.List;

public record SearchCoherenceConstraint(boolean mandatory, List<String> terms) {

    public int size() {
        return terms.size();
    }

    /** Create a new SearchCoherenceConstraint with the given terms, and the given mandatory flag.
     * Stop words are replaced with empty strings.
     */
    public static SearchCoherenceConstraint mandatory(String... terms) {
        return new SearchCoherenceConstraint(true, trimStopWords(terms));
    }
    /** Create a new SearchCoherenceConstraint with the given terms, and the given mandatory flag.
     * Stop words are replaced with empty strings.
     */
    public static SearchCoherenceConstraint mandatory(List<String> terms) {
        return new SearchCoherenceConstraint(true, trimStopWords(terms));
    }
    /** Create a new SearchCoherenceConstraint with the given terms, without the mandatory flag.
     * Stop words are replaced with empty strings.
     */
    public static SearchCoherenceConstraint optional(String... terms) {
        return new SearchCoherenceConstraint(false, trimStopWords(terms));
    }
    /** Create a new SearchCoherenceConstraint with the given terms, without the mandatory flag.
     * Stop words are replaced with empty strings.
     */
    public static SearchCoherenceConstraint optional(List<String> terms) {
        return new SearchCoherenceConstraint(false, trimStopWords(terms));
    }

    private static List<String> trimStopWords(List<String> terms) {
        List<String> ret = new ArrayList<>(terms.size());
        for (var term : terms) {
            if (WordPatterns.isStopWord(term)) {
                ret.add("");
            } else {
                ret.add(term);
            }
        }
        return List.copyOf(ret);
    }

    private static List<String> trimStopWords(String... terms) {
        List<String> ret = new ArrayList<>(terms.length);
        for (var term : terms) {
            if (WordPatterns.isStopWord(term)) {
                ret.add("");
            } else {
                ret.add(term);
            }
        }

        while (!ret.isEmpty() && "".equals(ret.getFirst())) {
            ret.removeFirst();
        }
        while (!ret.isEmpty() && "".equals(ret.getLast())) {
            ret.removeLast();
        }

        return List.copyOf(ret);
    }

}
