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

    private final List<EdgeDomain> deferredDomains = new ArrayList<>();
    private final List<EdgeUrl> deferredUrls = new ArrayList<>();

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
        sqlLoadDomainLinks.load(data, links);
    }

    @Override
    public void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip) {
        logger.debug("loadProcessedDomain({}, {}, {})", domain, state, ip);

        sqlLoadProcessedDomain.load(data, domain, state, ip);
    }

    @Override
    public void loadProcessedDocument(LoadProcessedDocument document) {
        deferralCheck(document.url());

        processedDocumentList.add(document);
    }

    @Override
    public void loadProcessedDocumentWithError(LoadProcessedDocumentWithError document) {
        deferralCheck(document.url());

        processedDocumentWithErrorList.add(document);
    }

    private void deferralCheck(EdgeUrl url) {
        if (data.getDomainId(url.domain) <= 0)
            deferredDomains.add(url.domain);

        if (data.getUrlId(url) <= 0)
            deferredUrls.add(url);
    }

    @Override
    public void loadKeywords(EdgeUrl url, DocumentKeywords[] words) {
        logger.debug("loadKeywords(#{})", words.length);

        // This is a bit of a bandaid safeguard against a bug in
        // in the converter, shouldn't be necessary in the future
        if (!deferredDomains.isEmpty()) {
            loadDomain(deferredDomains.toArray(EdgeDomain[]::new));
            deferredDomains.clear();
        }

        if (!deferredUrls.isEmpty()) {
            loadUrl(deferredUrls.toArray(EdgeUrl[]::new));
            deferredUrls.clear();
        }

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
