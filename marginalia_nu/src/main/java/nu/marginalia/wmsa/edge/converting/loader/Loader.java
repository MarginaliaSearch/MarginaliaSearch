package nu.marginalia.wmsa.edge.converting.loader;

import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocumentWithError;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Loader implements Interpreter {
    private final SqlLoadUrls sqlLoadUrls;
    private final SqlLoadDomains sqlLoadDomains;
    private final SqlLoadDomainLinks sqlLoadDomainLinks;
    private final SqlLoadProcessedDomain sqlLoadProcessedDomain;
    private final SqlLoadProcessedDocument sqlLoadProcessedDocument;
    private final IndexLoadKeywords indexLoadKeywords;

    private static final Logger logger = LoggerFactory.getLogger(Loader.class);

    private final List<LoadProcessedDocument> processedDocumentList;
    private final List<LoadProcessedDocumentWithError> processedDocumentWithErrorList;

    public final LoaderData data;

    public Loader(int sizeHint,
                  SqlLoadUrls sqlLoadUrls,
                  SqlLoadDomains sqlLoadDomains,
                  SqlLoadDomainLinks sqlLoadDomainLinks,
                  SqlLoadProcessedDomain sqlLoadProcessedDomain,
                  SqlLoadProcessedDocument sqlLoadProcessedDocument,
                  IndexLoadKeywords indexLoadKeywords)
    {
        data = new LoaderData(sizeHint);

        this.sqlLoadUrls = sqlLoadUrls;
        this.sqlLoadDomains = sqlLoadDomains;
        this.sqlLoadDomainLinks = sqlLoadDomainLinks;
        this.sqlLoadProcessedDomain = sqlLoadProcessedDomain;
        this.sqlLoadProcessedDocument = sqlLoadProcessedDocument;
        this.indexLoadKeywords = indexLoadKeywords;

        processedDocumentList = new ArrayList<>(sizeHint);
        processedDocumentWithErrorList = new ArrayList<>(sizeHint);
    }


    @Override
    public void loadUrl(EdgeUrl[] urls) {
        logger.debug("loadUrl({})", urls, null);

        sqlLoadUrls.load(data, urls);
    }

    @Override
    public void loadDomain(EdgeDomain[] domains) {
        logger.debug("loadDomain({})", domains, null);
        sqlLoadDomains.load(data, domains);
    }

    @Override
    public void loadRssFeed(EdgeUrl[] rssFeed) {
        logger.debug("loadRssFeed({})", rssFeed, null);
    }

    @Override
    public void loadDomainLink(DomainLink[] links) {
        logger.debug("loadDomainLink({})", links, null);
        sqlLoadDomainLinks.load(links);
    }

    @Override
    public void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, double quality) {
        logger.debug("loadProcessedDomain({}, {}, {})", domain, state, quality);
        sqlLoadProcessedDomain.load(data, domain, state, quality);
    }

    @Override
    public void loadProcessedDocument(LoadProcessedDocument document) {
        processedDocumentList.add(document);
    }

    @Override
    public void loadProcessedDocumentWithError(LoadProcessedDocumentWithError document) {
        processedDocumentWithErrorList.add(document);
    }

    @Override
    public void loadKeywords(EdgeUrl url, DocumentKeywords[] words) {
        logger.debug("loadKeywords(#{})", words.length);
        try {
            indexLoadKeywords.load(data, url, words);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadDomainRedirect(DomainLink link) {
        logger.debug("loadDomainRedirect({})", link);
        sqlLoadProcessedDomain.loadAlias(data, link);
    }

    public void finish() {
        sqlLoadProcessedDocument.load(data, processedDocumentList);
        sqlLoadProcessedDocument.loadWithError(data, processedDocumentWithErrorList);
    }
}
