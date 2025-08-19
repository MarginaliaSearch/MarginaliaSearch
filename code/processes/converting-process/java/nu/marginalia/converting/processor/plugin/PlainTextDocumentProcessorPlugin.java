package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.converting.processor.logic.PlainTextLogic;
import nu.marginalia.converting.util.LineUtils;
import nu.marginalia.domclassifier.DomSampleClassification;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import org.apache.commons.lang3.StringUtils;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;


public class PlainTextDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final int maxTitleLength;
    private final DocumentKeywordExtractor keywordExtractor;
    private final PlainTextLogic plainTextLogic = new PlainTextLogic();
    private final LanguageConfiguration languageConfiguration;
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentLengthLogic documentLengthLogic;

    private static boolean lenientProcessing = Boolean.getBoolean("converter.lenientProcessing");


    @Inject
    public PlainTextDocumentProcessorPlugin(@Named("max-title-length") Integer maxTitleLength,
                                            LanguageConfiguration languageConfiguration,
                                            ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                            DocumentKeywordExtractor keywordExtractor,
                                            DocumentLengthLogic documentLengthLogic
                                            )
    {
        this.languageConfiguration = languageConfiguration;
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.documentLengthLogic = documentLengthLogic;
        this.maxTitleLength = maxTitleLength;
        this.keywordExtractor = keywordExtractor;
    }

    @Override
    public boolean isApplicable(CrawledDocument doc) {
        String contentType = doc.contentType.toLowerCase();

        if (contentType.equals("text/plain"))
            return true;
        if (contentType.startsWith("text/plain;")) // charset=blabla
            return true;

        return false;
    }

    @Override
    public DetailsWithWords createDetails(CrawledDocument crawledDocument,
                                          LinkTexts linkTexts,
                                          Set<DomSampleClassification> domSampleClassifications, DocumentClass documentClass)
            throws DisqualifiedException, URISyntaxException {

        String documentBody = crawledDocument.documentBody();

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);

        var dld = sentenceExtractorProvider.get().extractSentences(documentBody, "");
        if (!lenientProcessing && !documentLengthLogic.validateLength(dld, 1.0)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LENGTH);
        }

        final String languageIsoCode = dld.language().isoCode();

        var ret = new ProcessedDocumentDetails();

        List<String> firstFewLines = LineUtils.firstNLines(documentBody, 40);

        ret.length = documentBody.length();

        ret.format = DocumentFormat.PLAIN;
        ret.title = StringUtils.truncate(plainTextLogic.getTitle(url, firstFewLines), maxTitleLength);
        ret.language = languageIsoCode;

        ret.quality = -1;

        ret.features = new HashSet<>();
        ret.description = StringUtils.truncate(plainTextLogic.getDescription(firstFewLines), 255);
        ret.hashCode = dld.localitySensitiveHashCode();

        final PubDate pubDate = new PubDate(LocalDate.ofYearDay(1993, 1));

        EnumSet<DocumentFlags> documentFlags = EnumSet.of(DocumentFlags.PlainText);

        ret.metadata = new DocumentMetadata(documentLengthLogic.getEncodedAverageLength(dld),
                pubDate.yearByte(), (int) -ret.quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, languageIsoCode, linkTexts, url);

        var tagWords = new MetaTagsBuilder()
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(ret.features)
                .addFormat(ret.format)
                .addLanguage(languageIsoCode)
                .build();

        words.addAllSyntheticTerms(tagWords);

        if (pubDate.hasYear()) {
            ret.pubYear = pubDate.year();
        }

        /* These are assumed to be populated */
        ret.linksInternal = new ArrayList<>();
        ret.linksExternal = new ArrayList<>();

        return new DetailsWithWords(ret, words);
    }


}
