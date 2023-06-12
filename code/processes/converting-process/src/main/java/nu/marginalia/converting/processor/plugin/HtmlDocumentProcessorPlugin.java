package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.processor.MetaRobotsTag;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.converting.processor.logic.dom.MeasureLengthVisitor;
import nu.marginalia.converting.processor.logic.links.FileLinks;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;

import static nu.marginalia.converting.model.DisqualifiedException.*;


public class HtmlDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final Logger logger = LoggerFactory.getLogger(getClass());
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

        final int length = getLength(doc);
        final HtmlStandard standard = getHtmlStandard(doc);
        final double quality = documentValuator.getQuality(crawledDocument, standard, doc, length);

        ret.length = length;
        ret.standard = standard;
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);
        ret.quality = quality;

        // don't move this up! it uses title and quality
        // and is run before the heavy computations below
        documentLengthLogic.validateLength(dld);
        if (isDisqualified(url, ret)) {
            throw new DisqualifiedException(DisqualificationReason.QUALITY);
        }

        final Set<HtmlFeature> features = featureExtractor.getFeatures(crawledDomain, doc, dld);
        ret.features = features;
        ret.hashCode = dld.localitySensitiveHashCode();

        PubDate pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, standard, true);
        EnumSet<DocumentFlags> documentFlags = htmlFeatures2DocumentFlags(features);

        ret.metadata = new DocumentMetadata(
                documentLengthLogic.getEncodedAverageLength(dld),
                pubDate.yearByte(), (int) -quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, url);

        ret.description = getDescription(doc, words.importantWords);

        var tagWords = new MetaTagsBuilder()
                .addDomainCrawlData(crawledDomain)
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(features)
                .addFormat(standard)
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

        words.addAllSyntheticTerms(FileLinks.createFileLinkKeywords(lp, domain));
        words.addAllSyntheticTerms(FileLinks.createFileEndingKeywords(doc));
        words.addAllSyntheticTerms(createLinkKeywords(lp));
    }

    private Set<String> createLinkKeywords(LinkProcessor lp) {
        final Set<String> linkTerms = new HashSet<>();

        for (var fd : lp.getForeignDomains()) {
            linkTerms.add("links:"+fd.toString().toLowerCase());
            linkTerms.add("links:"+fd.getDomain().toLowerCase());
        }

        return linkTerms;
    }

    private HtmlStandard getHtmlStandard(Document doc) {
        HtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(doc.documentType());

        if (HtmlStandard.UNKNOWN.equals(htmlStandard)) {
            return HtmlStandardExtractor.sniffHtmlStandard(doc);
        }
        return htmlStandard;
    }

    private String getDescription(Document doc,
                                  Set<String> importantWords)
    {
        List<String> cleanedWords = new ArrayList<>(importantWords.size());

        for (var word : importantWords) {
            // summary extraction is not interested in n-grams
            if (word.contains("_")) {
                continue;
            }

            cleanedWords.add(word);
        }

        return summaryExtractor.extractSummary(doc, cleanedWords);
    }

    private int getLength(Document doc) {
        var mlv = new MeasureLengthVisitor();
        doc.traverse(mlv);
        return mlv.length;
    }

}
