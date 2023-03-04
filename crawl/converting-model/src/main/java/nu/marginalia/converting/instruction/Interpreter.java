package nu.marginalia.converting.instruction;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.model.crawl.DocumentKeywords;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;

public interface Interpreter {
    void loadUrl(EdgeUrl[] url);
    void loadDomain(EdgeDomain[] domain);
    void loadRssFeed(EdgeUrl[] rssFeed);
    void loadDomainLink(DomainLink[] links);

    void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip);
    void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument);
    void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError);

    void loadKeywords(EdgeUrl url, EdgePageDocumentsMetadata metadata, DocumentKeywords words);

    void loadDomainRedirect(DomainLink link);
}
