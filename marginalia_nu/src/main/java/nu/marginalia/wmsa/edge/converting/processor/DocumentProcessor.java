package nu.marginalia.wmsa.edge.converting.processor;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.util.gregex.GuardedRegex;
import nu.marginalia.util.gregex.GuardedRegexFactory;
import nu.marginalia.util.language.LanguageFilter;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.sentence.SentenceExtractor;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.KeywordMetadata;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException;
import nu.marginalia.wmsa.edge.converting.model.DisqualifiedException.DisqualificationReason;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocumentDetails;
import nu.marginalia.wmsa.edge.converting.processor.logic.*;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDate;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateSniffer;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard.UNKNOWN;

public class DocumentProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int minDocumentLength;
    private final double minDocumentQuality;

    private static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml", "application/xhtml", "text/html");

    private final SentenceExtractor sentenceExtractor;
    private final FeatureExtractor featureExtractor;
    private final TitleExtractor titleExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final SummaryExtractor summaryExtractor;
    private final PubDateSniffer pubDateSniffer;

    private static final DocumentValuator documentValuator = new DocumentValuator();
    private static final LanguageFilter languageFilter = new LanguageFilter();
    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    @Inject
    public DocumentProcessor(@Named("min-document-length") Integer minDocumentLength,
                             @Named("min-document-quality") Double minDocumentQuality,
                             SentenceExtractor sentenceExtractor,
                             FeatureExtractor featureExtractor,
                             TitleExtractor titleExtractor,
                             DocumentKeywordExtractor keywordExtractor,
                             SummaryExtractor summaryExtractor,
                             PubDateSniffer pubDateSniffer)
    {
        this.minDocumentLength = minDocumentLength;
        this.minDocumentQuality = minDocumentQuality;
        this.sentenceExtractor = sentenceExtractor;
        this.featureExtractor = featureExtractor;
        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.summaryExtractor = summaryExtractor;
        this.pubDateSniffer = pubDateSniffer;
    }

    public ProcessedDocument makeDisqualifiedStub(CrawledDocument crawledDocument) {
        ProcessedDocument ret = new ProcessedDocument();

        try {
            ret.state = EdgeUrlState.DISQUALIFIED;
            ret.url = getDocumentUrl(crawledDocument);
        }
        catch (Exception ex) {}

        return ret;
    }

    public ProcessedDocument process(CrawledDocument crawledDocument, CrawledDomain crawledDomain) {
        ProcessedDocument ret = new ProcessedDocument();

        try {
            processDocument(crawledDocument, crawledDomain, ret);
        }
        catch (DisqualifiedException ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            ret.stateReason = ex.reason.toString();
            logger.debug("Disqualified {}: {}", ret.url, ex.reason);
        }
        catch (Exception ex) {
            ret.state = EdgeUrlState.DISQUALIFIED;
            ret.stateReason = DisqualificationReason.PROCESSING_EXCEPTION.toString();
            logger.info("Failed to convert " + crawledDocument.url, ex);
            ex.printStackTrace();
        }

        return ret;
    }

    private void processDocument(CrawledDocument crawledDocument, CrawledDomain crawledDomain, ProcessedDocument ret) throws URISyntaxException, DisqualifiedException {

        var crawlerStatus = CrawlerDocumentStatus.valueOf(crawledDocument.crawlerStatus);
        if (crawlerStatus != CrawlerDocumentStatus.OK) {
            throw new DisqualifiedException(crawlerStatus);
        }

        if (AcceptableAds.hasAcceptableAdsHeader(crawledDocument)) {
            throw new DisqualifiedException(DisqualificationReason.ACCEPTABLE_ADS);
        }

        if (!isAcceptedContentType(crawledDocument)) {
            throw new DisqualifiedException(DisqualificationReason.CONTENT_TYPE);
        }


        ret.url = getDocumentUrl(crawledDocument);
        ret.state = crawlerStatusToUrlState(crawledDocument.crawlerStatus, crawledDocument.httpStatus);

        var detailsWithWords = createDetails(crawledDomain, crawledDocument);

        ret.details = detailsWithWords.details();
        ret.words = detailsWithWords.words();
    }


    private EdgeUrl getDocumentUrl(CrawledDocument crawledDocument)
            throws URISyntaxException
    {
        if (crawledDocument.canonicalUrl != null) {
            try {
                return new EdgeUrl(crawledDocument.canonicalUrl);
            }
            catch (URISyntaxException ex) { /* fallthrough */ }
        }

        return new EdgeUrl(crawledDocument.url);
    }

    public static boolean isAcceptedContentType(CrawledDocument crawledDocument) {
        if (crawledDocument.contentType == null) {
            return false;
        }

        var ct = crawledDocument.contentType;

        if (acceptedContentTypes.contains(ct))
            return true;

        if (ct.contains(";")) {
            return acceptedContentTypes.contains(ct.substring(0, ct.indexOf(';')));
        }
        return false;
    }

    private EdgeUrlState crawlerStatusToUrlState(String crawlerStatus, int httpStatus) {
        return switch (CrawlerDocumentStatus.valueOf(crawlerStatus)) {
            case OK -> httpStatus < 300 ? EdgeUrlState.OK : EdgeUrlState.DEAD;
            case REDIRECT -> EdgeUrlState.REDIRECT;
            default -> EdgeUrlState.DEAD;
        };
    }

    private DetailsWithWords createDetails(CrawledDomain crawledDomain, CrawledDocument crawledDocument)
            throws DisqualifiedException, URISyntaxException {

        String documentBody = crawledDocument.documentBody.decode();

        if (languageFilter.isBlockedUnicodeRange(documentBody)) {
            throw new DisqualifiedException(DisqualificationReason.LANGUAGE);
        }

        Document doc = Jsoup.parse(documentBody);

        if (AcceptableAds.hasAcceptableAdsTag(doc)) {
            // I've never encountered a website where this hasn't been a severe indicator
            // of spam

            throw new DisqualifiedException(DisqualificationReason.ACCEPTABLE_ADS);
        }

        if (doc.select("meta[name=robots]").attr("content").contains("noindex")) {
            throw new DisqualifiedException(DisqualificationReason.FORBIDDEN);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        Document prunedDoc = doc.clone();

        prunedDoc.getElementsByTag("svg").remove();
        prunedDoc.body().filter(new DomPruningFilter(0.5));

        var dld = sentenceExtractor.extractSentences(prunedDoc);

        checkDocumentLanguage(dld);

        var ret = new ProcessedDocumentDetails();

        ret.length = getLength(doc);
        ret.standard = getHtmlStandard(doc);
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);

        ret.quality = documentValuator.getQuality(crawledDocument, ret.standard, doc, dld);
        ret.hashCode = HashCode.fromString(crawledDocument.documentBodyHash).asLong();

        KeywordMetadata keywordMetadata = new KeywordMetadata();

        PubDate pubDate;
        EdgePageWords words;
        if (shouldDoSimpleProcessing(url, dld, ret)) {
            /* Some documents we'll index, but only superficially. This is a compromise
               to allow them to be discoverable, without having them show up without specific
               queries. This also saves a lot of processing power.
             */
            ret.features = Set.of(HtmlFeature.UNKNOWN);
            words = keywordExtractor.extractKeywordsMinimal(dld, keywordMetadata);
            ret.description = "";

            pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, ret.standard, false);

            ret.metadata = new EdgePageDocumentsMetadata(url.depth(), pubDate.yearByte(), 0, (int) -ret.quality, EnumSet.of(EdgePageDocumentFlags.Simple));

        }
        else {
            ret.features = featureExtractor.getFeatures(crawledDomain, doc, dld);
            words = keywordExtractor.extractKeywords(dld, keywordMetadata);
            ret.description = getDescription(doc);

            pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, ret.standard, true);

            ret.metadata = new EdgePageDocumentsMetadata(url.depth(), pubDate.yearByte(), 0, (int) -ret.quality, EnumSet.noneOf(EdgePageDocumentFlags.class));
        }

        addMetaWords(ret, url, pubDate, crawledDomain, words);

        getLinks(url, ret, doc, words);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        return new DetailsWithWords(ret, words);
    }

    private static final GuardedRegex mastodonFeedRegex = GuardedRegexFactory.startsWith("/@", "^/@[^/]+/?$");

    private boolean shouldDoSimpleProcessing(EdgeUrl url, DocumentLanguageData dld, ProcessedDocumentDetails ret) {
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

    private void addMetaWords(ProcessedDocumentDetails ret, EdgeUrl url, PubDate pubDate, CrawledDomain domain, EdgePageWords words) {
        List<String> tagWords = new ArrayList<>();

        var edgeDomain = url.domain;
        tagWords.add("format:"+ret.standard.toString().toLowerCase());

        tagWords.add("site:" + edgeDomain.toString().toLowerCase());
        if (!Objects.equals(edgeDomain.toString(), edgeDomain.domain)) {
            tagWords.add("site:" + edgeDomain.domain.toLowerCase());
        }

        tagWords.add("tld:" + edgeDomain.getTld());

        tagWords.add("proto:"+url.proto.toLowerCase());
        tagWords.add("js:" + Boolean.toString(ret.features.contains(HtmlFeature.JS)).toLowerCase());

        if (domain.ip != null) {
            tagWords.add("ip:" + domain.ip.toLowerCase()); // lower case because IPv6 is hexadecimal
        }

        ret.features.stream().map(HtmlFeature::getKeyword).forEach(tagWords::add);

        if (pubDate.year() > 1900) {
            tagWords.add("year:" + pubDate.year());
        }
        if (pubDate.dateIso8601() != null) {
            tagWords.add("pub:" + pubDate.dateIso8601());
        }

        words.addAllSyntheticTerms(tagWords);
    }

    private void getLinks(EdgeUrl baseUrl, ProcessedDocumentDetails ret, Document doc, EdgePageWords words) {

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

        createLinkKeywords(words, lp);
        createFileLinkKeywords(words, lp, domain);
    }

    private void createLinkKeywords(EdgePageWords words, LinkProcessor lp) {
        final Set<String> linkTerms = new HashSet<>();

        for (var fd : lp.getForeignDomains()) {
            linkTerms.add("links:"+fd.toString().toLowerCase());
            linkTerms.add("links:"+fd.getDomain().toLowerCase());
        }
        words.addAllSyntheticTerms(linkTerms);
    }

    private void createFileLinkKeywords(EdgePageWords words, LinkProcessor lp, EdgeDomain domain) {
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

    private void checkDocumentLanguage(DocumentLanguageData dld) throws DisqualifiedException {
        double languageAgreement = languageFilter.dictionaryAgreement(dld);
        if (languageAgreement < 0.1) {
            throw new DisqualifiedException(DisqualificationReason.LANGUAGE);
        }
    }

    private EdgeHtmlStandard getHtmlStandard(Document doc) {
        EdgeHtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(doc.documentType());

        if (UNKNOWN.equals(htmlStandard)) {
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

    private record DetailsWithWords(ProcessedDocumentDetails details,
                                    EdgePageWords words) {}

}
