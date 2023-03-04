package nu.marginalia.loading.loader;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.crawl.DocumentKeywords;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
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
        sqlLoadUrls.load(data, urls);
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
    public void loadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip) {
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
    public void loadKeywords(EdgeUrl url, EdgePageDocumentsMetadata metadata, DocumentKeywords words) {
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
            indexLoadKeywords.load(data, url, metadata, words);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadDomainRedirect(DomainLink link) {
        sqlLoadProcessedDomain.loadAlias(data, link);
    }

    public void finish() {
        // Some work needs to be processed out of order for the database relations to work out

        sqlLoadProcessedDocument.load(data, processedDocumentList);
        sqlLoadProcessedDocument.loadWithError(data, processedDocumentWithErrorList);
    }

    public void close() {
        try {
            indexLoadKeywords.close();
        }
        catch (Exception ex) {
            logger.error("Error when closing the index loader", ex);
        }
    }
}
