package nu.marginalia.converting.processor;

import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.converting.model.ProcessedDocument;

import java.util.HashSet;
import java.util.Set;

public class DocumentDecorator {
    private final Set<String> extraSearchTerms = new HashSet<>();
    private final AnchorTextKeywords keywords;

    public DocumentDecorator(AnchorTextKeywords keywords) {
        this.keywords = keywords;
    }

    public void addTerm(String term) {
        extraSearchTerms.add(term);
    }

    public void apply(ProcessedDocument doc, DomainLinks externalDomainLinks) {
        if (doc == null)
            return;
        if (doc.words == null)
            return;

        doc.words.addAllSyntheticTerms(extraSearchTerms);
        doc.words.addAnchorTerms(keywords.getAnchorTextKeywords(externalDomainLinks, doc.url));
    }
}
