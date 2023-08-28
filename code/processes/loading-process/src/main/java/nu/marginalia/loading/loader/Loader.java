package nu.marginalia.loading.loader;

import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Loader implements Interpreter, AutoCloseable {
    private final SqlLoadDomains sqlLoadDomains;
    private final SqlLoadDomainLinks sqlLoadDomainLinks;
    private final SqlLoadProcessedDomain sqlLoadProcessedDomain;
    private final LdbLoadProcessedDocument loadProcessedDocument;
    private final SqlLoadDomainMetadata sqlLoadDomainMetadata;

    private final IndexLoadKeywords indexLoadKeywords;

    private static final Logger logger = LoggerFactory.getLogger(Loader.class);

    private final List<LoadProcessedDocument> processedDocumentList;
    private final List<LoadProcessedDocumentWithError> processedDocumentWithErrorList;


    public final LoaderData data;

    public Loader(int sizeHint,
                  OldDomains oldDomains,
                  SqlLoadDomains sqlLoadDomains,
                  SqlLoadDomainLinks sqlLoadDomainLinks,
                  SqlLoadProcessedDomain sqlLoadProcessedDomain,
                  LdbLoadProcessedDocument loadProcessedDocument,
                  SqlLoadDomainMetadata sqlLoadDomainMetadata,
                  IndexLoadKeywords indexLoadKeywords) {
        data = new LoaderData(oldDomains, sizeHint);

        this.sqlLoadDomains = sqlLoadDomains;
        this.sqlLoadDomainLinks = sqlLoadDomainLinks;
        this.sqlLoadProcessedDomain = sqlLoadProcessedDomain;
        this.loadProcessedDocument = loadProcessedDocument;
        this.sqlLoadDomainMetadata = sqlLoadDomainMetadata;
        this.indexLoadKeywords = indexLoadKeywords;

        processedDocumentList = new ArrayList<>(sizeHint);
        processedDocumentWithErrorList = new ArrayList<>(sizeHint);
    }

    @Override
    public void loadDomain(EdgeDomain[] domains) {
        sqlLoadDomains.load(data, domains);
    }

    @Override
    public void loadRssFeed(EdgeUrl[] rssFeed) {
        logger.debug("loadRssFeed({})", rssFeed, null);
    }

    @Override
    public void loadDomainLink(DomainLink[] links) {
        sqlLoadDomainLinks.load(data, links);
    }

    @Override
    public void loadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip) {
        sqlLoadProcessedDomain.load(data, domain, state, ip);
    }

    @Override
    public void loadProcessedDocument(LoadProcessedDocument document) {
        processedDocumentList.add(document);

        if (processedDocumentList.size() > 100) {
            loadProcessedDocument.load(data, processedDocumentList);
            processedDocumentList.clear();
        }
    }
    @Override
    public void loadProcessedDocumentWithError(LoadProcessedDocumentWithError document) {
        processedDocumentWithErrorList.add(document);

        if (processedDocumentWithErrorList.size() > 100) {
            loadProcessedDocument.loadWithError(data, processedDocumentWithErrorList);
            processedDocumentWithErrorList.clear();
        }
    }
    @Override
    public void loadKeywords(EdgeUrl url, int ordinal, int features, DocumentMetadata metadata, DocumentKeywords words) {
        indexLoadKeywords.load(data, ordinal, url, features, metadata, words);
    }

    @Override
    public void loadDomainRedirect(DomainLink link) {
        sqlLoadProcessedDomain.loadAlias(data, link);
    }

    @Override
    public void loadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) {
        sqlLoadDomainMetadata.load(data, domain, knownUrls, goodUrls, visitedUrls);
    }

    public void close() {
        if (processedDocumentList.size() > 0) {
            loadProcessedDocument.load(data, processedDocumentList);
        }
        if (processedDocumentWithErrorList.size() > 0) {
            loadProcessedDocument.loadWithError(data, processedDocumentWithErrorList);
        }
    }

}
