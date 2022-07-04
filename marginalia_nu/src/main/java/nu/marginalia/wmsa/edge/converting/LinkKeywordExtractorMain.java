package nu.marginalia.wmsa.edge.converting;

import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.wmsa.edge.converting.atags.AnchorTextExtractor;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainReader;
import nu.marginalia.wmsa.edge.crawling.CrawlerSpecificationLoader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.integration.stackoverflow.StackOverflowPostsReader;
import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaReader;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class LinkKeywordExtractorMain {
    private static final Logger logger = LoggerFactory.getLogger(LinkKeywordExtractorMain.class);

    public static void main(String... args) throws IOException, InterruptedException {

        if (args.length < 2) {
            System.err.println("Arguments: [crawl|so|wiki] crawl-plan.yaml [data]");
            System.exit(0);
        }

        String command = args[0];
        var plan = new CrawlPlanLoader().load(Path.of(args[1]));

        switch (command) {
            case "crawl": getKeywordsFromCrawl(plan); break;
            case "so": getKeywordsFromSo(plan, args[2]); break;
            case "wiki": getKeywordsFromWiki(plan, args[2]); break;
            default: System.err.println("Unrecognized command");
        }

    }

    private static void getKeywordsFromWiki(EdgeCrawlPlan plan, String arg) throws IOException, InterruptedException {


        HashSet<String> crawledDomains = new HashSet<>();
        TIntHashSet crawledUrls = new TIntHashSet(50_000_000);

        logger.info("Loading URLs");
        Files.lines(Path.of("/home/vlofgren/good-urls3.txt"))
                .filter(url -> !url.contains("stackoverflow") && !url.contains("stackexchange"))
                .mapToInt(String::hashCode)
                .forEach(crawledUrls::add);

        logger.info("Loading input spec");
        CrawlerSpecificationLoader.readInputSpec(plan.getJobSpec(),
                spec -> { crawledDomains.add(spec.domain); });

        try (var output = new UrlKeywordTsvWriter(Path.of("links.tsv"))) {
            AnchorTextExtractor anchorTextExtractor = new AnchorTextExtractor(domain -> crawledDomains.contains(domain)
                    && !domain.contains("wiki")
                    && !domain.contains("isni")
                    && !domain.contains("wiktionary"),
                    url -> crawledUrls.contains(url.toString().hashCode()),
                    output::write);

            new WikipediaReader(arg, new EdgeDomain("invalid.example"), article -> {
                anchorTextExtractor.processDocument(article.getUrl().toString(), article.body);
            }).join();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }



    }

    private static void getKeywordsFromSo(EdgeCrawlPlan plan, String arg) throws IOException, InterruptedException {
        TIntHashSet crawledUrls = new TIntHashSet(50_000_000);

        logger.info("Loading URLs");
        Files.lines(Path.of("/home/vlofgren/good-urls3.txt"))
                .filter(url -> !url.contains("stackoverflow") && !url.contains("stackexchange"))
                .mapToInt(String::hashCode)
                .forEach(crawledUrls::add);

        logger.info("Loading input spec");

        HashSet<String> crawledDomains = new HashSet<>();
        CrawlerSpecificationLoader.readInputSpec(plan.getJobSpec(),
                spec -> crawledDomains.add(spec.domain));

        crawledDomains.remove("jsfiddle.net"); // like 30% of SO's links go here
        crawledDomains.remove("jsbin.com");
        crawledDomains.remove("codepad.org");


        try (var output = new UrlKeywordTsvWriter(Path.of("links.tsv"))) {
            AnchorTextExtractor anchorTextExtractor = new AnchorTextExtractor(crawledDomains::contains,
                url -> crawledUrls.contains(url.toString().hashCode()),
                output::write);

            new StackOverflowPostsReader(arg, new EdgeDomain("invalid.example"), post -> {
                anchorTextExtractor.processDocument(post.getUrl().toString(), post.fullBody);
            }).join();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    public static void getKeywordsFromCrawl(EdgeCrawlPlan plan) throws IOException {

        TIntHashSet crawledUrls = new TIntHashSet(50_000_000);

        logger.info("Loading URLs");
        Files.lines(Path.of("/home/vlofgren/good-urls3.txt"))
                .filter(url -> !url.contains("stackoverflow") && !url.contains("stackexchange"))
                .mapToInt(String::hashCode)
                .forEach(crawledUrls::add);


        logger.info("Loading input spec");

        HashSet<String> crawledDomains = new HashSet<>();
        CrawlerSpecificationLoader.readInputSpec(plan.getJobSpec(),
                spec -> crawledDomains.add(spec.domain));

        List<String> fileNames = new ArrayList<>();

        logger.info("Replaying crawl log");
        WorkLog.readLog(plan.crawl.getLogFile(),
                entry -> fileNames.add(entry.path()));

        try (var output = new UrlKeywordTsvWriter(Path.of("links.tsv"))) {
            AnchorTextExtractor anchorTextExtractor = new AnchorTextExtractor(crawledDomains::contains,
                    url -> url.params != null,
                    //url -> crawledUrls.contains(url.toString().hashCode()),
                    output::write);

            logger.info("Reading files");
            for (var fn : fileNames) {
                CrawledDomainReader crawledDomainReader = new CrawledDomainReader();
                var crawledDomain = crawledDomainReader.read(plan.getCrawledFilePath(fn));
                if (crawledDomain.doc == null) continue;

                System.out.println("# " + crawledDomain.domain);

                for (var doc : crawledDomain.doc) {
                    if (Objects.equals(doc.crawlerStatus, CrawlerDocumentStatus.OK.name())) {
                        anchorTextExtractor.processDocument(doc.url, doc.documentBody);
                    }
                }
            }
        }

    }

    private static class UrlKeywordTsvWriter implements AutoCloseable {

        private final OutputStream stream;

        UrlKeywordTsvWriter(Path outputFile) throws IOException {
            this.stream = new BufferedOutputStream(new FileOutputStream(outputFile.toFile()));
        }

        void write(EdgeUrl url, String keyword) {
            try {
                stream.write(url.toString().getBytes());
                stream.write('\t');
                stream.write(keyword.getBytes());
                stream.write('\n');
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

}
