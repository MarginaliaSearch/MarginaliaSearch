package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.processor.logic.PlainTextLogic;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.util.LineUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;


public class PlainTextDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final int maxTitleLength;
    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final PlainTextLogic plainTextLogic = new PlainTextLogic();
    private final DocumentLengthLogic documentLengthLogic;


    @Inject
    public PlainTextDocumentProcessorPlugin(@Named("max-title-length") Integer maxTitleLength,
                                            SentenceExtractor sentenceExtractor,
                                            DocumentKeywordExtractor keywordExtractor,
                                            DocumentLengthLogic documentLengthLogic
                                            )
    {
        this.documentLengthLogic = documentLengthLogic;
        this.maxTitleLength = maxTitleLength;
        this.sentenceExtractor = sentenceExtractor;
        this.keywordExtractor = keywordExtractor;
    }

    @Override
    public boolean isApplicable(CrawledDocument doc) {
        return doc.contentType.equalsIgnoreCase("text/plain");
    }

    @Override
    public DetailsWithWords createDetails(CrawledDomain crawledDomain, CrawledDocument crawledDocument)
            throws DisqualifiedException, URISyntaxException {

        String documentBody = crawledDocument.documentBody.decode();

        if (languageFilter.isBlockedUnicodeRange(documentBody)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LANGUAGE);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        var dld = sentenceExtractor.extractSentences(documentBody, "");

        checkDocumentLanguage(dld);

        documentLengthLogic.validateLength(dld);

        var ret = new ProcessedDocumentDetails();

        List<String> firstFewLines = LineUtils.firstNLines(documentBody, 40);

        ret.length = documentBody.length();

        ret.standard = HtmlStandard.PLAIN;
        ret.title = StringUtils.truncate(plainTextLogic.getTitle(url, firstFewLines), maxTitleLength);

        ret.quality = -1;

        ret.features = new HashSet<>();
        ret.description = StringUtils.truncate(plainTextLogic.getDescription(firstFewLines), 255);
        ret.hashCode = dld.localitySensitiveHashCode();

        final PubDate pubDate = new PubDate(LocalDate.ofYearDay(1993, 1));

        EnumSet<DocumentFlags> documentFlags = EnumSet.of(DocumentFlags.PlainText);

        ret.metadata = new DocumentMetadata(documentLengthLogic.getEncodedAverageLength(dld),
                url.depth(), pubDate.yearByte(), (int) -ret.quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, url);

        var tagWords = new MetaTagsBuilder()
                .addDomainCrawlData(crawledDomain)
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(ret.features)
                .addFormat(ret.standard)
                .build();

        words.addAllSyntheticTerms(tagWords);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        /* These are assumed to be populated */
        ret.linksInternal = new ArrayList<>();
        ret.linksExternal = new ArrayList<>();
        ret.feedLinks = new ArrayList<>();

        return new DetailsWithWords(ret, words);
    }


}
