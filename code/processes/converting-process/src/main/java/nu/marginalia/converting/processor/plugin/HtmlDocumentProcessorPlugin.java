package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.processor.MetaRobotsTag;
import nu.marginalia.converting.processor.logic.dom.MeasureLengthVisitor;
import nu.marginalia.converting.processor.logic.links.FileLinks;
import nu.marginalia.converting.processor.logic.links.LinkProcessor;
import nu.marginalia.converting.processor.plugin.specialization.DefaultSpecialization;
import nu.marginalia.converting.processor.plugin.specialization.HtmlProcessorSpecialization;
import nu.marginalia.converting.processor.plugin.specialization.LemmySpecialization;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.crawl.HtmlFeature;
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
    private final PubDateSniffer pubDateSniffer;

    private final DocumentLengthLogic documentLengthLogic;

    private final MetaRobotsTag metaRobotsTag;
    private final DocumentGeneratorExtractor documentGeneratorExtractor;
    private static final DocumentValuator documentValuator = new DocumentValuator();

    private static final LinkParser linkParser = new LinkParser();
    private static final FeedExtractor feedExtractor = new FeedExtractor(linkParser);

    private final DefaultSpecialization defaultSpecialization;
    private final LemmySpecialization lemmySpecialization;

    @Inject
    public HtmlDocumentProcessorPlugin(
            @Named("min-document-quality") Double minDocumentQuality,
            SentenceExtractor sentenceExtractor,
            FeatureExtractor featureExtractor,
            TitleExtractor titleExtractor,
            DocumentKeywordExtractor keywordExtractor,
            PubDateSniffer pubDateSniffer,
            DocumentLengthLogic documentLengthLogic,
            MetaRobotsTag metaRobotsTag,
            DocumentGeneratorExtractor documentGeneratorExtractor, DefaultSpecialization defaultSpecialization, LemmySpecialization lemmySpecialization) {
        this.documentLengthLogic = documentLengthLogic;
        this.minDocumentQuality = minDocumentQuality;
        this.sentenceExtractor = sentenceExtractor;
        this.featureExtractor = featureExtractor;

        this.titleExtractor = titleExtractor;
        this.keywordExtractor = keywordExtractor;
        this.pubDateSniffer = pubDateSniffer;
        this.metaRobotsTag = metaRobotsTag;

        this.documentGeneratorExtractor = documentGeneratorExtractor;
        this.defaultSpecialization = defaultSpecialization;
        this.lemmySpecialization = lemmySpecialization;
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

        final var generatorParts = documentGeneratorExtractor.generatorCleaned(doc);

        final var specialization = selectSpecialization(generatorParts);

        if (!specialization.shouldIndex(url)) {
            throw new DisqualifiedException(DisqualificationReason.IRRELEVANT);
        }

        DocumentLanguageData dld = sentenceExtractor.extractSentences(specialization.prune(doc));

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
        documentLengthLogic.validateLength(dld, specialization.lengthModifier());
        if (isDisqualified(url, ret)) {
            throw new DisqualifiedException(DisqualificationReason.QUALITY);
        }

        final Set<HtmlFeature> features = featureExtractor.getFeatures(crawledDomain, doc, dld);
        ret.features = features;
        ret.hashCode = dld.localitySensitiveHashCode();

        PubDate pubDate = pubDateSniffer.getPubDate(crawledDocument.headers, url, doc, standard, true);

        EnumSet<DocumentFlags> documentFlags = documentFlags(features, generatorParts.type());

        ret.metadata = new DocumentMetadata(
                documentLengthLogic.getEncodedAverageLength(dld),
                pubDate.yearByte(), (int) -quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, url);

        ret.description = specialization.getSummary(doc, words.importantWords);
        ret.generator = generatorParts.type();

        var tagWords = new MetaTagsBuilder()
                .addDomainCrawlData(crawledDomain)
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(features)
                .addFormat(standard)
                .addGenerator(generatorParts.keywords())
                .build();

        words.addAllSyntheticTerms(tagWords);

        getLinks(url, ret, doc, words);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        return new DetailsWithWords(ret, words);
    }

    /** Depending on the generator tag, we may want to use specialized logic for pruning and summarizing the document */
    private HtmlProcessorSpecialization selectSpecialization(DocumentGeneratorExtractor.DocumentGenerator generatorParts) {

        if (generatorParts.keywords().contains("lemmy")) {
          return lemmySpecialization;
        }

        return defaultSpecialization;
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

    private int getLength(Document doc) {
        var mlv = new MeasureLengthVisitor();
        doc.traverse(mlv);
        return mlv.length;
    }

}
