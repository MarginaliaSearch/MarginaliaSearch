package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.language.filter.LanguageFilter;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.MetaRobotsTag;
import nu.marginalia.converting.processor.logic.dom.MeasureLengthVisitor;
import nu.marginalia.converting.processor.logic.links.FileLinks;
import nu.marginalia.converting.processor.logic.links.LinkProcessor;
import nu.marginalia.converting.processor.plugin.specialization.*;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.link_parser.FeedExtractor;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.model.html.HtmlStandard;
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

    private final FeatureExtractor featureExtractor;
    private final TitleExtractor titleExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final PubDateSniffer pubDateSniffer;

    private final DocumentLengthLogic documentLengthLogic;

    private final MetaRobotsTag metaRobotsTag;
    private final DocumentGeneratorExtractor documentGeneratorExtractor;
    private static final DocumentValuator documentValuator = new DocumentValuator();

    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final HtmlProcessorSpecializations htmlProcessorSpecializations;

    private static final int MAX_DOCUMENT_LENGTH_BYTES = Integer.getInteger("converter.max-body-length",128_000);

    @Inject
    public HtmlDocumentProcessorPlugin(
            @Named("min-document-quality") Double minDocumentQuality,
            LanguageFilter languageFilter,
            FeatureExtractor featureExtractor,
            TitleExtractor titleExtractor,
            DocumentKeywordExtractor keywordExtractor,
            PubDateSniffer pubDateSniffer,
            DocumentLengthLogic documentLengthLogic,
            MetaRobotsTag metaRobotsTag,
            DocumentGeneratorExtractor documentGeneratorExtractor,
            ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
            HtmlProcessorSpecializations specializations)
    {
        super(languageFilter);

        this.documentLengthLogic = documentLengthLogic;
        this.minDocumentQuality = minDocumentQuality;
        this.featureExtractor = featureExtractor;

        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.pubDateSniffer = pubDateSniffer;
        this.metaRobotsTag = metaRobotsTag;

        this.documentGeneratorExtractor = documentGeneratorExtractor;
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.htmlProcessorSpecializations = specializations;
    }

    @Override
    public boolean isApplicable(CrawledDocument doc) {
        return doc.contentType.toLowerCase().contains("html");
    }

    @Override
    public DetailsWithWords createDetails(CrawledDocument crawledDocument, DocumentClass documentClass)
            throws DisqualifiedException, URISyntaxException {

        String documentBody = crawledDocument.documentBody;

        if (languageFilter.isBlockedUnicodeRange(documentBody)) {
            throw new DisqualifiedException(DisqualificationReason.LANGUAGE);
        }

        if (documentBody.length() > MAX_DOCUMENT_LENGTH_BYTES) { // 128kb
            documentBody = documentBody.substring(0, MAX_DOCUMENT_LENGTH_BYTES);
        }

        Document doc = Jsoup.parse(documentBody);

        if (!metaRobotsTag.allowIndexingByMetaTag(doc)) {
            throw new DisqualifiedException(DisqualificationReason.FORBIDDEN);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        final var generatorParts = documentGeneratorExtractor.detectGenerator(doc, crawledDocument.headers);

        final var specialization = htmlProcessorSpecializations.select(generatorParts, url);

        if (!specialization.shouldIndex(url)) {
            throw new DisqualifiedException(DisqualificationReason.IRRELEVANT);
        }

        DocumentLanguageData dld =
                sentenceExtractorProvider.get().extractSentences(specialization.prune(doc));

        checkDocumentLanguage(dld);

        var ret = new ProcessedDocumentDetails();

        final int length = getLength(doc);
        final HtmlStandard standard = getHtmlStandard(doc);
        final double quality = documentValuator.getQuality(crawledDocument, standard, doc, length);

        ret.length = length;
        ret.standard = standard;
        ret.title = titleExtractor.getTitleAbbreviated(doc, dld, crawledDocument.url);

        documentLengthLogic.validateLength(dld, specialization.lengthModifier() * documentClass.lengthLimitModifier());

        final Set<HtmlFeature> features = featureExtractor.getFeatures(url, doc, dld);

        ret.features = features;
        ret.quality = documentValuator.adjustQuality(quality, features);
        ret.hashCode = dld.localitySensitiveHashCode();

        if (isDisqualified(documentClass, url, quality, ret.title)) {
            throw new DisqualifiedException(DisqualificationReason.QUALITY);
        }

        PubDate pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, standard, true);

        EnumSet<DocumentFlags> documentFlags = documentFlags(features, generatorParts.type());

        ret.metadata = new DocumentMetadata(
                documentLengthLogic.getEncodedAverageLength(dld),
                pubDate.yearByte(),
                (int) -ret.quality, // ret.quality is negative
                documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, url);

        ret.description = specialization.getSummary(doc, words.importantWords);
        ret.generator = generatorParts.type();

        var tagWords = new MetaTagsBuilder()
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(features)
                .addFormat(standard)
                .addGenerator(generatorParts.keywords())
                .build();


        words.addAllSyntheticTerms(tagWords);
        specialization.amendWords(doc, words);

        getLinks(url, ret, doc, words);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        return new DetailsWithWords(ret, words);
    }

    private EnumSet<DocumentFlags> documentFlags(Set<HtmlFeature> features, GeneratorType type) {
        EnumSet<DocumentFlags> flags = EnumSet.noneOf(DocumentFlags.class);

        if (features.contains(HtmlFeature.JS)) {
            flags.add(DocumentFlags.Javascript);
        }

        switch (type) {
            case DOCS -> flags.add(DocumentFlags.GeneratorDocs);
            case FORUM -> flags.add(DocumentFlags.GeneratorForum);
            case WIKI -> flags.add(DocumentFlags.GeneratorWiki);
            default -> {} // no flags
        }

        return flags;
    }

    private static final GuardedRegex mastodonFeedRegex = GuardedRegexFactory.startsWith("/@", "^/@[^/]+/?$");

    private boolean isDisqualified(DocumentClass documentClass,
                                   EdgeUrl url,
                                   double quality,
                                   String title) {

        if (documentClass.enforceQualityLimits()
            && quality < minDocumentQuality)
        {
            return true;
        }

        // These pages shouldn't be publicly accessible
        if ("phpinfo()".equals(title)) {
            return true;
        }

        // Urls that look like /@foo are typically Mastodon or other twitter-like feeds,
        // we don't want to index them because they change so rapidly; subdirectories are
        // fine though
        //
        if (mastodonFeedRegex.test(url.path)) {
            return true;
        }

        // Annoying blog crap
        if (url.path.contains("/tag/") && url.path.endsWith("/")) {
            return true;
        }
        if (url.path.contains("/tags/") && url.path.endsWith("/")) {
            return true;
        }
        if (url.path.contains("/category/") && url.path.endsWith("/")) {
            return true;
        }
        if (url.path.contains("/categories/") && url.path.endsWith("/")) {
            return true;
        }
        if (url.path.contains("/section/") && url.path.endsWith("/")) {
            return true;
        }
        if (url.path.contains("/sections/") && url.path.endsWith("/")) {
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
        for (var meta : doc.select("meta[http-equiv=refresh]")) {
            linkParser.parseMetaRedirect(baseUrl, meta).ifPresent(lp::accept);
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
            linkTerms.add("links:"+fd.getTopDomain().toLowerCase());
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

    private int getLength(Document doc) {
        var mlv = new MeasureLengthVisitor();
        doc.traverse(mlv);
        return mlv.length;
    }

}
