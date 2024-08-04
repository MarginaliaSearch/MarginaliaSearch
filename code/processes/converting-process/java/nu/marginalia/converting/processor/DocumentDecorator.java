package nu.marginalia.converting.processor;

import nu.marginalia.converting.model.ProcessedDocument;

import java.util.HashSet;
import java.util.Set;

public class DocumentDecorator {
    private final Set<String> extraSearchTerms = new HashSet<>();

    public DocumentDecorator() {
    }

    public void addTerm(String term) {
        extraSearchTerms.add(term);
    }

    public void apply(ProcessedDocument doc) {
        if (doc == null)
            return;
        if (doc.words == null)
            return;

        doc.words.addAllSyntheticTerms(extraSearchTerms);
    }
}
