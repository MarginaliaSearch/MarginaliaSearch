package nu.marginalia.wmsa.edge.converting;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.util.DenseBitMap;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainReader;
import nu.marginalia.wmsa.edge.crawling.CrawlerSpecificationLoader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class LinkKeywordExtractorMain {
    private static final Logger logger = LoggerFactory.getLogger(LinkKeywordExtractorMain.class);

    public static void main(String... args) throws IOException {

        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new ConverterModule(plan)
        );

        injector.getInstance(LinkKeywordExtractorMain.class);
    }

    private final HashSet<String> crawledDomains = new HashSet<>();
    private final List<String> fileNames = new ArrayList<>();
    private final LinkParser linkParser = new LinkParser();
    private final SentenceExtractor sentenceExtractor = new SentenceExtractor(WmsaHome.getLanguageModels());

    private final HashFunction hashFunction = Hashing.murmur3_128();

    // This bit map is used as a bloom filter to deduplicate url-keyword combinations
    // false positives are expected, but that's an acceptable trade-off to not have to deal with
    // de-duplicating billions of shuffled (url, word) tuples on limited hardware
    private final DenseBitMap deduplicateHashBitset = new DenseBitMap(DenseBitMap.MAX_CAPACITY_2GB_16BN_ITEMS);

    @Inject
    public LinkKeywordExtractorMain(EdgeCrawlPlan plan) throws IOException {
        logger.info("Loading input spec");

        CrawlerSpecificationLoader.readInputSpec(plan.getJobSpec(),
                spec -> crawledDomains.add(spec.domain));

        logger.info("Replaying crawl log");
        WorkLog.readLog(plan.crawl.getLogFile(),
                entry -> fileNames.add(entry.path()));

        logger.info("Reading files");
        for (var fn : fileNames) {
            CrawledDomainReader crawledDomainReader = new CrawledDomainReader();
            var crawledDomain = crawledDomainReader.read(plan.getCrawledFilePath(fn));
            if (crawledDomain.doc == null) continue;

            System.out.println("# " + crawledDomain.domain);

            for (var doc : crawledDomain.doc) {
                try {
                    if (Objects.equals(doc.crawlerStatus, CrawlerDocumentStatus.OK.name())) {
                        processDocument(doc.url, doc.documentBody);
                    }
                }
                catch (URISyntaxException ex) {
                    // This Shouldn't Happen (TM) as the URL that we're failing to process
                    // is expected to have already been parsed by this code successfully
                    // in the process of getting here.
                    //
                    // But also, if it does happen, it's no big deal

                    logger.warn("Bad URL format", ex);
                }
            }
        }
    }

    private final Pattern anchorTextNoise = Pattern.compile("[\\s\"()“”:]+");

    private void processDocument(String docUrl, String documentBody) throws URISyntaxException {
        var processed = Jsoup.parse(documentBody);

        EdgeUrl documentUrl = new EdgeUrl(docUrl);

        for (var link : processed.getElementsByTag("a")) {
            if (link.hasAttr("href")) {
                String href = link.attr("href");
                String text = anchorTextNoise.matcher(link.text().toLowerCase()).replaceAll(" ").trim();

                processAnchor(documentUrl, href, text);
            }
        }
    }

    private void processAnchor(EdgeUrl documentUrl, String href, String text) {
        if (!isInterestingAnchorText(text)) {
            return;
        }

        var optLinkUrl = linkParser.parseLink(documentUrl, href);
        if (optLinkUrl.isEmpty()) return;

        var linkUrl = optLinkUrl.get();

        if (!isInterestingAnchorLink(linkUrl)) {
            return;
        }

        DocumentLanguageData languageData = sentenceExtractor.extractSentences(text);
        for (var sent : languageData.sentences) {
            for (var wordPos : sent) {
                if (wordPos.isStopWord())
                    continue;

                String word = wordPos.wordLowerCase();

                if (!WordPatterns.filter(word))
                    continue;

                if (!linkUrl.domain.equals(documentUrl.domain)) {
                    if (isNewKeywordForLink(word, linkUrl.toString())) {
                        System.out.println(linkUrl + "\t" + word);
                    }
                }
            }
        }
    }

    // This pattern doesn't need to perfectly capture all anchor texts that are URLs, if it gets 95% that's fine
    private final Predicate<String> looksLikeAnURL = Pattern.compile("(\\p{Alpha}+://)?[\\p{Alnum}.]+(/[^/]+)+").asMatchPredicate();

    private boolean isInterestingAnchorText(String text) {
        if (text.isBlank()) return false;
        if (text.length() > 32) return false;

        // Google loves questions, and so does SEO spammers
        if (text.endsWith("?")) return false;

        if (text.startsWith("http:") || text.startsWith("https:")) return false;

        if (looksLikeAnURL.test(text)) return false;

        return switch (text) {
            case "this", "here", "click", "click here", "download", "source" -> false;
            default -> true;
        };
    }

    private boolean isInterestingAnchorLink(EdgeUrl linkUrl) {
        if (!(linkUrl.proto.endsWith("http") || linkUrl.proto.equals("https"))) {
            return false;
        }

        return crawledDomains.contains(linkUrl.domain.toString());
    }

    private boolean isNewKeywordForLink(String href, String text) {
        long hash = 0;

        hash ^= hashFunction.hashString(href, StandardCharsets.UTF_8).asLong();
        hash ^= hashFunction.hashString(text, StandardCharsets.UTF_8).asLong();

        // Remove sign bit because we don't want a negative index in deduplicateHashBitset
        hash &= 0x7FFF_FFFF_FFFF_FFFFL;

        return !deduplicateHashBitset.set(hash % deduplicateHashBitset.cardinality);
    }
}
