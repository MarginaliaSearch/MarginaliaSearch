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
    void loadUrl(EdgeUrl[] url);
    void loadDomain(EdgeDomain[] domain);
    void loadRssFeed(EdgeUrl[] rssFeed);
    void loadDomainLink(DomainLink[] links);

    void loadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip);
    void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument);
    void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError);

    void loadKeywords(EdgeUrl url, DocumentMetadata metadata, DocumentKeywords words);

    void loadDomainRedirect(DomainLink link);

    void loadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls);
}
