package nu.marginalia.loading.loader;

import com.google.inject.Inject;

public class LoaderFactory {
    private final SqlLoadUrls sqlLoadUrls;
    private final SqlLoadDomains sqlLoadDomains;
    private final SqlLoadDomainLinks sqlLoadDomainLinks;
    private final SqlLoadProcessedDomain sqlLoadProcessedDomain;
    private final SqlLoadProcessedDocument sqlLoadProcessedDocument;
    private final SqlLoadDomainMetadata sqlLoadDomainMetadata;
    private final IndexLoadKeywords indexLoadKeywords;

    @Inject
    public LoaderFactory(SqlLoadUrls sqlLoadUrls,
                         SqlLoadDomains sqlLoadDomains,
                         SqlLoadDomainLinks sqlLoadDomainLinks,
                         SqlLoadProcessedDomain sqlLoadProcessedDomain,
                         SqlLoadProcessedDocument sqlLoadProcessedDocument,
                         SqlLoadDomainMetadata sqlLoadDomainMetadata,
                         IndexLoadKeywords indexLoadKeywords) {

        this.sqlLoadUrls = sqlLoadUrls;
        this.sqlLoadDomains = sqlLoadDomains;
        this.sqlLoadDomainLinks = sqlLoadDomainLinks;
        this.sqlLoadProcessedDomain = sqlLoadProcessedDomain;
        this.sqlLoadProcessedDocument = sqlLoadProcessedDocument;
        this.sqlLoadDomainMetadata = sqlLoadDomainMetadata;
        this.indexLoadKeywords = indexLoadKeywords;
    }

    public Loader create(int sizeHint) {
        return new Loader(sizeHint, sqlLoadUrls, sqlLoadDomains, sqlLoadDomainLinks, sqlLoadProcessedDomain, sqlLoadProcessedDocument, sqlLoadDomainMetadata, indexLoadKeywords);
    }
}
