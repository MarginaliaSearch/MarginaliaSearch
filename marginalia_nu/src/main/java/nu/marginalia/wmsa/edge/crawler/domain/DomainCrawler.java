package nu.marginalia.wmsa.edge.crawler.domain;

import crawlercommons.robots.SimpleRobotRules;
import gnu.trove.set.hash.TIntHashSet;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.PlainTextProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.crawler.worker.UrlBlocklist;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.*;
import org.apache.logging.log4j.util.Strings;
import org.apache.logging.log4j.util.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DomainCrawler {
    private final HttpFetcher fetcher;
    private final EdgeDomain indexDomain;
    private int maxDepth;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final double EXT_LINK_SCORE_THRESHOLD = -15;
    public final static int MIN_WORDS_PER_DOCUMENT = 100;
    public final static int DEFAULT_CRAWL_DELAY_MS = 1000;

    private final LinkedList<EdgeUrl> queue = new LinkedList<>();
    private final Set<EdgeUrl> visited = new HashSet<>();
    private final TIntHashSet visitedHash = new TIntHashSet();

    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);


    private static final UrlsCache<EdgeUrl> URLS_CACHE = new UrlsCache<>();
    private final int pass;
    private final int maxExtLinks;
    private final int maxIntLinks;

    private final IpBlockList ipBlockList;

    private final PlainTextProcessor plainTextProcessor;
    private final HtmlProcessor htmlProcessor;
    private final ArchiveClient archiveClient;
    private final DomainCrawlerRobotsTxt domainRobotsTxtFetcher;
    private final LanguageFilter languageFilter;
    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();
    private final double rank;

    public EdgeDomain domain() {
        return indexDomain;
    }

    public DomainCrawler(HttpFetcher fetcher,
                         PlainTextProcessor plainTextProcessor,
                         HtmlProcessor htmlProcessor,
                         ArchiveClient archiveClient,
                         DomainCrawlerRobotsTxt domainRobotsTxtFetcher,
                         LanguageFilter languageFilter,
                         EdgeIndexTask ingress,
                         IpBlockList ipBlockList) {
        this.fetcher = fetcher;
        this.plainTextProcessor = plainTextProcessor;
        this.htmlProcessor = htmlProcessor;
        this.archiveClient = archiveClient;
        this.domainRobotsTxtFetcher = domainRobotsTxtFetcher;
        this.languageFilter = languageFilter;
        this.ipBlockList = ipBlockList;
        this.indexDomain = ingress.domain;

        this.pass = ingress.pass;
        this.rank = ingress.rank;
        this.maxExtLinks = ingress.limit * 50;
        this.maxIntLinks = 100 + ingress.limit * 5;

        if (ingress.pass == 0) {
            this.maxDepth = 25;
        }
        else {
            this.maxDepth = 100;
        }

        ingress.streamUrls().forEach(queue::add);
        visitedHash.addAll(ingress.visited);
    }


    public DomainCrawlResults crawlToExhaustion(int maxCount, Supplier<Boolean> continueSignal) {
        maxDepth = maxCount;

        var robotsTxtRules = domainRobotsTxtFetcher.fetchRulesCached(indexDomain);
        final DomainCrawlResults results = new DomainCrawlResults(indexDomain, rank, pass);

        fetcher.clearCookies();

        crawlDelay(0, robotsTxtRules);

        int count = 0;
        while (!queue.isEmpty() && count < maxCount) {
            if (!continueSignal.get()) break;

            if (crawlNextUrl(results, robotsTxtRules) != 0)
                count++;
        }

        addLinkWords(results);
        
        results.feeds.removeIf(url -> isDeadUrl(robotsTxtRules, url));
        results.intUrl.removeAll(results.visitedUrl);

        return results;
    }

    public DomainCrawlResults crawl() {
        var robotsTxtRules = domainRobotsTxtFetcher.fetchRulesCached(indexDomain);
        final DomainCrawlResults results = new DomainCrawlResults(indexDomain, rank, pass);

        fetcher.clearCookies();

        double scoreRemaining = 2*maxDepth;

        Comparator<EdgeUrl> comparator = Comparator.comparing(u ->
                results.pageContents.values().stream().filter(contents -> contents.hasHotLink(u)).count());
        Comparator<EdgeUrl> comparator2 = comparator.thenComparing(EdgeUrl::depth);

        crawlDelay(0, robotsTxtRules);

        while (!queue.isEmpty() && scoreRemaining > 0) {
            queue.sort(comparator2);
            scoreRemaining += crawlNextUrl(results, robotsTxtRules);
        }

        addLinkWords(results);

        results.feeds.removeIf(url -> isDeadUrl(robotsTxtRules, url));
        results.intUrl.removeAll(results.visitedUrl);

        return results;
    }

    private double crawlNextUrl(DomainCrawlResults results, SimpleRobotRules robotsTxtRules) {
        EdgeUrl url = queue.removeFirst();

        if (visitedHash.contains(url.hashCode())) {
            return 0.;
        }
        results.visitedUrl.add(url);

        if (!robotsTxtRules.isAllowed(url.toString()) ||
                urlBlocklist.isUrlBlocked(url) ||
                isUrlTooLong(url)) {
            results.urlStates.put(url, EdgeUrlState.DISQUALIFIED);
            return 0.;
        }

        if (!visited.add(url))
        {
            return 0.;
        }

        logger.debug("{}", url);

        return fetchUrl(robotsTxtRules, results, url);
    }

    private void addLinkWords(DomainCrawlResults results) {

        results.pageContents.values().forEach(page -> {
            page.linkWords.forEach((url,words) -> {
                if (words.isEmpty()) return;

                var dest = results.pageContents.get(url);
                if (dest != null) {
                    logger.debug("Amending title words {} -> {}", url, words);
                    var namesWords = dest.words.get(IndexBlock.Link);
                    namesWords.words.forEach(words::remove);
                    dest.words.append(IndexBlock.Link, words);
                }
            });
        });

    }

    private boolean isDeadUrl(SimpleRobotRules robotsTxtRules, EdgeUrl edgeUrl) {
        try {
            if (!robotsTxtRules.isAllowed(edgeUrl.toString())) {
                return false;
            }

            long tStart = System.currentTimeMillis();
            fetcher.fetchContent(edgeUrl);

            crawlDelay(System.currentTimeMillis() - tStart, robotsTxtRules);

            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

    private boolean isUrlTooLong(EdgeUrl url) {
        return url.path.length() > 255;
    }

    private boolean isEquivalentUrl(EdgeUrl a, EdgeUrl b) {
        if ((a == null) != (b == null))
            return false;
        if (a == b)
            return true;
        if (!Objects.equals(a.domain, b.domain)) {
            return false;
        }
        if (!Objects.equals(a.path, b.path)) {
            return false;
        }
        return true;
    }

    private double fetchUrl(SimpleRobotRules rules, DomainCrawlResults results, EdgeUrl url) {
        try {
            var page = fetcher.fetchContent(url);

            if (!isEquivalentUrl(page.redirectUrl, page.url)) {
                handleLink(results, page.redirectUrl, -1);
                results.urlStates.put(url, EdgeUrlState.REDIRECT);

                logger.debug("Redirect {} -> {}", url, page.redirectUrl);

                return -1;
            }
            final long startTime = System.currentTimeMillis();
            final long stopTime;

            var contents = parseContent(results, page);
            if (contents.isPresent()) {
                var content = contents.get();

                results.pageContents.put(content.url, content);

                archiveClient.submitPage(Context.internal(), url, page);
            }
            else {
                results.urlStates.put(url, EdgeUrlState.DISQUALIFIED);
            }

            stopTime = System.currentTimeMillis();
            crawlDelay(stopTime - startTime, rules);

            return contents.map(c -> c.getMetadata().quality()).orElse(-5.);
        }
        catch (HttpFetcher.BadContentType ex) {
            results.urlStates.put(url, EdgeUrlState.DISQUALIFIED);

            logger.debug("Bad content type {}", ex.getMessage());
            return -.1;
        }
        catch (Exception ex) {
            results.urlStates.put(url, EdgeUrlState.DEAD);

            if (logger.isDebugEnabled()) {
                ex.printStackTrace();
                logger.debug("Failed to crawl url {} : {} - {}", url, ex.getClass().getSimpleName(), ex.getMessage());
            }
            return -.5;
        }

    }

    private Optional<EdgePageContent> parseContent(DomainCrawlResults results, EdgeRawPageContents content) {
        var contentType = content.contentType.getContentType();

        if (content.data.length() < 500) {
            return Optional.empty();
        }
        switch (contentType) {
            case "application/xhtml+xml":
            case "application/xhtml":
            case "text/html": return parseHtmlContent(results, content);
            case "text/plain": return plainTextProcessor.parsePlainText(content);
            default: {
                logger.debug("Skipping contentType {}", content.contentType);
                return Optional.empty();
            }
        }
    }

    private Optional<EdgePageContent> parseHtmlContent(DomainCrawlResults results, EdgeRawPageContents rawContents) {

        var parsed = parseRawContents(rawContents.data);

        var langTagIsInteresting
                = languageFilter.isPageInterestingByHtmlTag(parsed)
                    .or(() -> languageFilter.isPageInterestingByMetaLanguage(parsed));

        if (langTagIsInteresting.isPresent() && !langTagIsInteresting.get()) {
            logger.debug("Rejected due to language tag");
            return Optional.empty();
        }

        var canonicalUrl = Optional.ofNullable(parsed.select("meta[rel=canonical]"))
                .map(tag -> tag.attr("href"))
                .filter(Strings::isNotBlank)
                .flatMap(url -> linkParser.parseLink(rawContents.url, url))
                .filter(url -> !url.equals(rawContents.url));

        if (canonicalUrl.isPresent()) {
            logger.debug("Noncanonical {} -> {}", rawContents.url, canonicalUrl.get());
            handleLink(results, canonicalUrl.get(), -1);
            return Optional.empty();
        }

        var processedPageContents = htmlProcessor.processHtmlPage(rawContents, parsed);
        if (processedPageContents == null) {
            logger.debug("Empty Processed Data for {}", rawContents.url);
            return Optional.empty();
        }

        extractLinks(results, rawContents, parsed, processedPageContents);

        if (processedPageContents.numWords() < MIN_WORDS_PER_DOCUMENT) {
            logger.debug("Rejected because too few words {} - {}", rawContents.url, processedPageContents.numWords());
            return Optional.empty();
        }
        if (processedPageContents.metadata.title.startsWith("Index of")) {
            return Optional.empty();
        }

        return Optional.of(processedPageContents);
    }

    private Document parseRawContents(String data) {
        return Jsoup.parse(data);
    }

    private void extractLinks(DomainCrawlResults results, EdgeRawPageContents rawContents, Document parsed, EdgePageContent processedPageContents) {
        var links = parsed.getElementsByTag("a");

        Observable.fromStream(links.stream())
                .mapOptional(elem -> linkParser.parseLink(rawContents.url, elem))
                .blockingForEach(link -> handleLink(results, link, processedPageContents.metadata.quality()));

        var frames = parsed.getElementsByTag("frame");

        Observable.fromStream(frames.stream())
                .mapOptional(elem -> linkParser.parseFrame(rawContents.url, elem))
                .blockingForEach(link -> handleLink(results, link, processedPageContents.metadata.quality()));

        parsed.select("link[rel=alternate]").forEach(alternateTag ->
                feedExtractor
                        .getFeedFromAlternateTag(rawContents.url,  alternateTag)
                        .ifPresent(results.feeds::add)

        );
    }

    private void handleLink(DomainCrawlResults results, EdgeUrl linkUrl, double pageScore) {
        if (!isProtoSupported(linkUrl.proto)) {
            return;
        }

        if (!linkUrl.domain.equals(indexDomain)) {
            handleExternalLink(results, pageScore, linkUrl);
        }
        else if (!isLinkVisited(linkUrl)
                && !urlBlocklist.isForumLink(linkUrl)
                && !urlBlocklist.isUrlBlocked(linkUrl)
        ) {
            enqueueUrl(linkUrl);

            if (results.intUrl.size() < maxIntLinks) {
                results.intUrl.add(linkUrl);
            }
        }
    }

    private boolean isProtoSupported(String proto) {
        return switch (proto.toLowerCase()) {
            case "http", "https" -> true;
            default -> false;
        };
    }

    private void handleExternalLink(DomainCrawlResults results,
                                    double pageScore,
                                    EdgeUrl linkUrl) {
        if (pageScore <= EXT_LINK_SCORE_THRESHOLD)
            return;
        if (results.extUrl.size() > maxExtLinks)
            return;
        if (isBlacklistedDomain(linkUrl.domain))
            return;
        if (urlBlocklist.isForumLink(linkUrl)) {
            return;
        }
        if (urlBlocklist.isUrlBlocked(linkUrl)) {
            return;
        }

        if (URLS_CACHE.add(linkUrl)) {
            results.extUrl.add(linkUrl);
        }
        results.links.add(new EdgeDomainLink(indexDomain, linkUrl.domain));
    }


    private boolean isBlacklistedDomain(EdgeDomain domain) {
        if ((isRestrictedTLD(domain.getAddress()))
                && !("".equals(domain.subDomain) || "www".equals(domain.subDomain)))
            return true;

        if (!ipBlockList.isAllowed(domain)) {
            return true;
        }

        if (isBlacklistedSubdomain(domain.subDomain))
            return true;

        return false;
    }

    private boolean isRestrictedTLD(String domain) {
        if (domain.contains("blog")) {
            return true;
        }

        if (domain.endsWith(".se"))
            return false;
        if (domain.endsWith(".nu"))
            return false;
        if (domain.endsWith(".uk"))
            return false;
        if (domain.endsWith(".jp"))
            return false;
        if (domain.endsWith(".com"))
            return false;
        if (domain.endsWith(".net"))
            return false;
        if (domain.endsWith(".org"))
            return false;
        if (domain.endsWith(".edu"))
            return false;

        return true;
    }

    private boolean isBlacklistedSubdomain(String subDomain) {
        if (subDomain.equals("git")) {
            return true;
        }
        else if (subDomain.contains("mirror")) {
            return true;
        }
        else if (subDomain.equals("docs")) {
            return true;
        }
        else if (subDomain.equals("mail")) {
            return true;
        }
        else if (subDomain.contains("list")) {
            return true;
        }
        else if (subDomain.startsWith("ftp")) {
            return true;
        }

        return false;
    }

    private boolean isLinkVisited(EdgeUrl linkUrl) {
        return visited.contains(linkUrl)
                || visitedHash.contains(linkUrl.hashCode());
    }

    private void enqueueUrl(EdgeUrl url) {
        if (visited.size() + queue.size() > maxDepth) {
            return;
        }
        queue.addLast(url);
    }

    @SneakyThrows
    public static void crawlDelay(long timeParsed, SimpleRobotRules rules) {
        var delay = rules.getCrawlDelay();
        if (delay >= 1) {
            if (timeParsed*1000 > delay)
                return;

            Thread.sleep(Math.min(1000*delay-timeParsed, 5000));
        }
        else {
            if (timeParsed > DEFAULT_CRAWL_DELAY_MS)
                return;

            Thread.sleep(DEFAULT_CRAWL_DELAY_MS - timeParsed);
        }
    }


}
