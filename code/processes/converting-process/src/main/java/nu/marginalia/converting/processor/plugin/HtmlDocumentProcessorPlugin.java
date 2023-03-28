package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.processor.MetaRobotsTag;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.converting.processor.logic.links.LinkProcessor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.summary.SummaryExtractor;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.converting.processor.logic.*;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.gregex.GuardedRegex;
import nu.marginalia.gregex.GuardedRegexFactory;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.pubdate.PubDateSniffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static nu.marginalia.converting.model.DisqualifiedException.*;


public class HtmlDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final double minDocumentQuality;

    private final SentenceExtractor sentenceExtractor;
    private final FeatureExtractor featureExtractor;
    private final TitleExtractor titleExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final SummaryExtractor summaryExtractor;
    private final PubDateSniffer pubDateSniffer;

    private final DocumentLengthLogic documentLengthLogic;

    private final MetaRobotsTag metaRobotsTag;
    private static final DocumentValuator documentValuator = new DocumentValuator();

    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    @Inject
    public HtmlDocumentProcessorPlugin(
            @Named("min-document-quality") Double minDocumentQuality,
            SentenceExtractor sentenceExtractor,
            FeatureExtractor featureExtractor,
            TitleExtractor titleExtractor,
            DocumentKeywordExtractor keywordExtractor,
            SummaryExtractor summaryExtractor,
            PubDateSniffer pubDateSniffer,
            DocumentLengthLogic documentLengthLogic,
            MetaRobotsTag metaRobotsTag) {
        this.documentLengthLogic = documentLengthLogic;
        this.minDocumentQuality = minDocumentQuality;
        this.sentenceExtractor = sentenceExtractor;
        this.featureExtractor = featureExtractor;

        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.summaryExtractor = summaryExtractor;
        this.pubDateSniffer = pubDateSniffer;
        this.metaRobotsTag = metaRobotsTag;

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

        if (!metaRobotsTag.allowIndexingByMetaTag(doc)) {
            throw new DisqualifiedException(DisqualificationReason.FORBIDDEN);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        DocumentLanguageData dld = sentenceExtractor.extractSentences(prune(doc));

        checkDocumentLanguage(dld);

        var ret = new ProcessedDocumentDetails();

        ret.length = getLength(doc);
        ret.standard = getHtmlStandard(doc);
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);
        ret.quality = documentValuator.getQuality(crawledDocument, ret.standard, doc);

        // don't move this up! it uses title and quality
        // and is run before the heavy computations below
        documentLengthLogic.validateLength(dld);
        if (isDisqualified(url, ret)) {
            throw new DisqualifiedException(DisqualificationReason.QUALITY);
        }

        ret.features = featureExtractor.getFeatures(crawledDomain, doc, dld);
        ret.description = getDescription(doc);
        ret.hashCode = dld.localitySensitiveHashCode();

        PubDate pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, ret.standard, true);
        EnumSet<DocumentFlags> documentFlags = htmlFeatures2DocumentFlags(ret.features);

        documentLengthLogic.setLengthFlags(ret.length, documentFlags);

        ret.metadata = new DocumentMetadata(url.depth(), pubDate.yearByte(), 0, (int) -ret.quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, url);

        var tagWords = new MetaTagsBuilder()
                .addDomainCrawlData(crawledDomain)
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(ret.features)
                .addFormat(ret.standard)
                .build();

        words.addAllSyntheticTerms(tagWords);

        getLinks(url, ret, doc, words);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        return new DetailsWithWords(ret, words);
    }

    private EnumSet<DocumentFlags> htmlFeatures2DocumentFlags(Set<HtmlFeature> features) {
        EnumSet<DocumentFlags> flags = EnumSet.noneOf(DocumentFlags.class);

        if (features.contains(HtmlFeature.ADVERTISEMENT)) {
            flags.add(DocumentFlags.Ads);
        }
        if (features.contains(HtmlFeature.JS)) {
            flags.add(DocumentFlags.Javascript);
        }
        if (features.contains(HtmlFeature.TRACKING)) {
            flags.add(DocumentFlags.Tracking);
        }

        return flags;
    }

    private Document prune(Document doc) {
        final var prunedDoc = doc.clone();

        prunedDoc.getElementsByTag("svg").remove();
        prunedDoc.body().filter(new DomPruningFilter(0.5));

        return prunedDoc;
    }


    private static final GuardedRegex mastodonFeedRegex = GuardedRegexFactory.startsWith("/@", "^/@[^/]+/?$");

    private boolean isDisqualified(EdgeUrl url, ProcessedDocumentDetails ret) {
        if (ret.quality < minDocumentQuality) {
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

    private HtmlStandard getHtmlStandard(Document doc) {
        HtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(doc.documentType());

        if (HtmlStandard.UNKNOWN.equals(htmlStandard)) {
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
