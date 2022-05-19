package nu.marginalia.wmsa.edge.crawler.domain;

import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.PlainTextProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;

public class DomainCrawlerFactory {
    private final HttpFetcher fetcher;
    private final HtmlProcessor htmlProcessor;
    private final ArchiveClient archiveClient;
    private DomainCrawlerRobotsTxt domainCrawlerRobotsTxt;
    private LanguageFilter languageFilter;
    private final IpBlockList blockList;
    private final PlainTextProcessor plainTextProcessor;

    @Inject
    public DomainCrawlerFactory(HttpFetcher fetcher,
                                HtmlProcessor htmlProcessor,
                                PlainTextProcessor plainTextProcessor, ArchiveClient archiveClient,
                                DomainCrawlerRobotsTxt domainCrawlerRobotsTxt,
                                LanguageFilter languageFilter,
                                IpBlockList blockList) {
        this.fetcher = fetcher;
        this.htmlProcessor = htmlProcessor;
        this.plainTextProcessor = plainTextProcessor;
        this.archiveClient = archiveClient;
        this.domainCrawlerRobotsTxt = domainCrawlerRobotsTxt;
        this.languageFilter = languageFilter;
        this.blockList = blockList;
    }

    public DomainCrawler domainCrawler(EdgeIndexTask indexTask) {
        return new DomainCrawler(fetcher, plainTextProcessor, htmlProcessor, archiveClient, domainCrawlerRobotsTxt, languageFilter, indexTask, blockList);
    }
}
