package nu.marginalia.loading.loader;

import com.google.inject.Inject;

public class LoaderFactory {
    private final SqlLoadUrls sqlLoadUrls;
    private final SqlLoadDomains sqlLoadDomains;
    private final SqlLoadDomainLinks sqlLoadDomainLinks;
    private final SqlLoadProcessedDomain sqlLoadProcessedDomain;
    private final SqlLoadProcessedDocument sqlLoadProcessedDocument;
    private final IndexLoadKeywords indexLoadKeywords;

    @Inject
    public LoaderFactory(SqlLoadUrls sqlLoadUrls,
                         SqlLoadDomains sqlLoadDomains,
                         SqlLoadDomainLinks sqlLoadDomainLinks,
                         SqlLoadProcessedDomain sqlLoadProcessedDomain,
                         SqlLoadProcessedDocument sqlLoadProcessedDocument,
                         IndexLoadKeywords indexLoadKeywords) {

        this.sqlLoadUrls = sqlLoadUrls;
        this.sqlLoadDomains = sqlLoadDomains;
        this.sqlLoadDomainLinks = sqlLoadDomainLinks;
        this.sqlLoadProcessedDomain = sqlLoadProcessedDomain;
        this.sqlLoadProcessedDocument = sqlLoadProcessedDocument;
        this.indexLoadKeywords = indexLoadKeywords;
    }

    public Loader create(int sizeHint) {
        return new Loader(sizeHint, sqlLoadUrls, sqlLoadDomains, sqlLoadDomainLinks, sqlLoadProcessedDomain, sqlLoadProcessedDocument, indexLoadKeywords);
    }
}
