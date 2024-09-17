package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.language.WordPatterns;

import java.util.ArrayList;
import java.util.List;

public sealed interface SearchPhraseConstraint {

    record Mandatory(List<String> terms) implements SearchPhraseConstraint {
        public Mandatory(String... terms) {
            this(List.of(terms));
        }
    }

    record Optional(List<String> terms) implements SearchPhraseConstraint {
        public Optional(String... terms) {
            this(List.of(terms));
        }
    }

    record Full(List<String> terms) implements SearchPhraseConstraint {
        public Full(String... terms) {
            this(List.of(terms));
        }
    }

    List<String> terms();
    default int size() {
        return terms().size();
    }

    static SearchPhraseConstraint mandatory(String... terms) {
        return new Mandatory(trimStopWords(terms));
    }
    static SearchPhraseConstraint mandatory(List<String> terms) {
        return new Mandatory(trimStopWords(terms));
    }
    static SearchPhraseConstraint optional(String... terms) {
        return new Optional(trimStopWords(terms));
    }
    static SearchPhraseConstraint optional(List<String> terms) {
        return new Optional(trimStopWords(terms));
    }
    static SearchPhraseConstraint full(String... terms) {
        return new Full(trimStopWords(terms));
    }
    static SearchPhraseConstraint full(List<String> terms) {
        return new Full(trimStopWords(terms));
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
