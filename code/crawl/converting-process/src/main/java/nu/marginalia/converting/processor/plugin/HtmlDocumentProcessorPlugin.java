package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.processor.logic.links.LinkProcessor;
import nu.marginalia.converting.processor.logic.summary.SummaryExtractor;
import nu.marginalia.crawling.common.link.LinkParser;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.language.model.KeywordMetadata;
import nu.marginalia.converting.processor.keywords.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.converting.model.DocumentKeywordsBuilder;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.converting.processor.logic.*;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateSniffer;
import nu.marginalia.gregex.GuardedRegex;
import nu.marginalia.gregex.GuardedRegexFactory;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static nu.marginalia.converting.model.DisqualifiedException.*;


public class HtmlDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final int minDocumentLength;
    private final double minDocumentQuality;

    private final SentenceExtractor sentenceExtractor;
    private final FeatureExtractor featureExtractor;
    private final TitleExtractor titleExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final SummaryExtractor summaryExtractor;
    private final PubDateSniffer pubDateSniffer;

    private static final DocumentValuator documentValuator = new DocumentValuator();

    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    @Inject
    public HtmlDocumentProcessorPlugin(@Named("min-document-length") Integer minDocumentLength,
                                       @Named("min-document-quality") Double minDocumentQuality,
                                       SentenceExtractor sentenceExtractor,
                                       FeatureExtractor featureExtractor,
                                       TitleExtractor titleExtractor,
                                       DocumentKeywordExtractor keywordExtractor,
                                       SummaryExtractor summaryExtractor,
                                       PubDateSniffer pubDateSniffer) {
        this.minDocumentLength = minDocumentLength;
        this.minDocumentQuality = minDocumentQuality;
        this.sentenceExtractor = sentenceExtractor;
        this.featureExtractor = featureExtractor;

        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.summaryExtractor = summaryExtractor;
        this.pubDateSniffer = pubDateSniffer;
    }

    @Override
    public boolean isApplicable(CrawledDocument doc) {
        return doc.contentType.toLowerCase().contains("html");
    }

    @Override
    public DetailsWithWords createDetails(CrawledDomain crawledDomain, CrawledDocument crawledDocument)
            throws DisqualifiedException, URISyntaxException {

        String documentBody = crawledDocument.documentBody.decode();

        if (languageFilter.isBlockedUnicodeRange(documentBody)) {
            throw new DisqualifiedException(DisqualificationReason.LANGUAGE);
        }

        Document doc = Jsoup.parse(documentBody);

        if (doc.select("meta[name=robots]").attr("content").contains("noindex")) {
            throw new DisqualifiedException(DisqualificationReason.FORBIDDEN);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        Document prunedDoc = prune(doc);

        var dld = sentenceExtractor.extractSentences(prunedDoc);

        checkDocumentLanguage(dld);

        var ret = new ProcessedDocumentDetails();

        ret.length = getLength(doc);
        ret.standard = getHtmlStandard(doc);
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);
        ret.quality = documentValuator.getQuality(crawledDocument, ret.standard, doc, dld);

        // don't move this up! it uses title and quality
        // and is run before the heavy computations below
        if (isDisqualified(url, dld, ret)) {
            throw new DisqualifiedException(DisqualificationReason.QUALITY);
        }

        ret.features = featureExtractor.getFeatures(crawledDomain, doc, dld);
        ret.description = getDescription(doc);
        ret.hashCode = dld.localitySensitiveHashCode();

        PubDate pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, ret.standard, true);
        ret.metadata = new DocumentMetadata(url.depth(), pubDate.yearByte(), 0, (int) -ret.quality, EnumSet.noneOf(EdgePageDocumentFlags.class));

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld);

        new MetaTagsBuilder()
                .addDomainCrawlData(crawledDomain)
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(ret.features)
                .addFormat(ret.standard)
                .build(words);

        getLinks(url, ret, doc, words);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        return new DetailsWithWords(ret, words);
    }

    private Document prune(Document doc) {
        final var prunedDoc = doc.clone();

        prunedDoc.getElementsByTag("svg").remove();
        prunedDoc.body().filter(new DomPruningFilter(0.5));

        return prunedDoc;
    }


    private static final GuardedRegex mastodonFeedRegex = GuardedRegexFactory.startsWith("/@", "^/@[^/]+/?$");

    private boolean isDisqualified(EdgeUrl url, DocumentLanguageData dld, ProcessedDocumentDetails ret) {
        if (ret.quality < minDocumentQuality) {
            return true;
        }
        if (dld.totalNumWords() < minDocumentLength) {
            return true;
        }
        // These pages shouldn't be publicly accessible
        if ("phpinfo()".equals(ret.title)) {
            return true;
        }

        // Urls that look like /@foo are typically Mastodon or other twitter-like feeds,
        // we don't want to index them because they change so rapidly; subdirectories are
        // fine though
        //
        if (mastodonFeedRegex.test(url.path)) {
            return true;
        }

        // Annoying wordpress crap
        if (url.path.startsWith("/tag/") && url.path.endsWith("/")) {
            return true;
        }
        return false;
    }


    private void getLinks(EdgeUrl baseUrl, ProcessedDocumentDetails ret, Document doc, DocumentKeywordsBuilder words) {

        final LinkProcessor lp = new LinkProcessor(ret, baseUrl);

        baseUrl = linkParser.getBaseLink(doc, baseUrl);

        EdgeDomain domain = baseUrl.domain;

        for (var atag : doc.getElementsByTag("a")) {
            var linkOpt = linkParser.parseLinkPermissive(baseUrl, atag);
            if (linkParser.shouldIndexLink(atag)) {
                linkOpt.ifPresent(lp::accept);
            }
            else {
                linkOpt
                        .filter(url -> linkParser.hasBinarySuffix(url.path.toLowerCase()))
                        .ifPresent(lp::acceptNonIndexable);
            }
        }
        for (var frame : doc.getElementsByTag("frame")) {
            linkParser.parseFrame(baseUrl, frame).ifPresent(lp::accept);
        }
        for (var frame : doc.getElementsByTag("iframe")) {
            linkParser.parseFrame(baseUrl, frame).ifPresent(lp::accept);
        }
        for (var link : doc.select("link[rel=alternate]")) {
            feedExtractor
                    .getFeedFromAlternateTag(baseUrl, link)
                    .ifPresent(lp::acceptFeed);
        }

        createFileLinkKeywords(words, lp, domain);
        createLinkKeywords(words, lp);
    }

    // If a document links to a file on the same server, and that file has
    // a salient file ending, then add the filename as a keyword so that it can
    // be found
    private void createFileLinkKeywords(DocumentKeywordsBuilder words, LinkProcessor lp, EdgeDomain domain) {
        Set<String> fileKeywords = new HashSet<>(100);
        for (var link : lp.getNonIndexableUrls()) {

            if (!domain.hasSameTopDomain(link.domain)) {
                continue;
            }

            synthesizeFilenameKeyword(fileKeywords, link);

        }

        words.addAllSyntheticTerms(fileKeywords);
    }

    private void synthesizeFilenameKeyword(Set<String> fileKeywords, EdgeUrl link) {

        Path pFilename = Path.of(link.path.toLowerCase()).getFileName();

        if (pFilename == null) return;

        String filename = pFilename.toString();
        if (filename.length() > 32
                || filename.endsWith(".xml")
                || filename.endsWith(".jpg")
                || filename.endsWith(".png")
                || filename.endsWith(".pdf")
                || filename.endsWith(".gif"))
            return;

        fileKeywords.add(filename.replace(' ', '_'));
    }

    private void createLinkKeywords(DocumentKeywordsBuilder words, LinkProcessor lp) {
        final Set<String> linkTerms = new HashSet<>();

        for (var fd : lp.getForeignDomains()) {
            linkTerms.add("links:"+fd.toString().toLowerCase());
            linkTerms.add("links:"+fd.getDomain().toLowerCase());
        }
        words.addAllSyntheticTerms(linkTerms);
    }

    private EdgeHtmlStandard getHtmlStandard(Document doc) {
        EdgeHtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(doc.documentType());

        if (EdgeHtmlStandard.UNKNOWN.equals(htmlStandard)) {
            return HtmlStandardExtractor.sniffHtmlStandard(doc);
        }
        return htmlStandard;
    }

    private String getDescription(Document doc) {
        return summaryExtractor.extractSummary(doc);
    }

    private int getLength(Document doc) {
        return doc.text().length();
    }
}
