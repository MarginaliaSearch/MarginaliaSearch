package nu.marginalia.wmsa.edge.crawler;


import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlerRobotsTxt;
import nu.marginalia.wmsa.edge.crawler.domain.RssCrawler;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import org.mariadb.jdbc.Driver;

import java.io.IOException;

public class RssScraperMain {

    public static void main(String... args) throws IOException {
//        Driver driver = new Driver();
//
//        var conn = new DatabaseModule().provideConnection();
//        var fetcher = new HttpFetcher("search.marginalia.nu");
//        var indexClient = new EdgeIndexClient();
//        indexClient.waitReady();
//
//        new RssCrawler(conn,
//                new DomainCrawlerRobotsTxt(fetcher, "search.marginalia.nu"),
//                new EdgeDataStoreDaoImpl(conn),
//                fetcher,
//                keywordExtractor, sentenceExtractor, indexClient).run();
    }

}
