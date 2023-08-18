package nu.marginalia.converting.instruction;

import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;

public interface Interpreter {
    default void loadUrl(EdgeUrl[] url) {}
    default void loadDomain(EdgeDomain[] domain) {}
    default void loadRssFeed(EdgeUrl[] rssFeed) {}
    default void loadDomainLink(DomainLink[] links) {}

    default void loadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip) {}
    default void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument) {}
    default void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError) {}

    default void loadKeywords(EdgeUrl url, int features, DocumentMetadata metadata, DocumentKeywords words) {}

    default void loadDomainRedirect(DomainLink link) {}

    default void loadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) {}
}
