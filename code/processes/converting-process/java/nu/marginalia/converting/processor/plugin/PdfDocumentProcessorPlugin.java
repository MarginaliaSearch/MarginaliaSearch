package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.converting.processor.logic.PlainTextLogic;
import nu.marginalia.converting.util.LineUtils;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.filter.LanguageFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;


public class PdfDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final int maxTitleLength;
    private final DocumentKeywordExtractor keywordExtractor;
    private final PlainTextLogic plainTextLogic = new PlainTextLogic();
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentLengthLogic documentLengthLogic;


    @Inject
    public PdfDocumentProcessorPlugin(@Named("max-title-length") Integer maxTitleLength,
                                      LanguageFilter languageFilter,
                                      ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                      DocumentKeywordExtractor keywordExtractor,
                                      DocumentLengthLogic documentLengthLogic
                                            )
    {
        super(languageFilter);
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.documentLengthLogic = documentLengthLogic;
        this.maxTitleLength = maxTitleLength;
        this.keywordExtractor = keywordExtractor;
    }

    @Override
    public boolean isApplicable(CrawledDocument doc) {
        String contentType = doc.contentType.toLowerCase();

        if (contentType.equals("application/pdf"))
            return true;
        if (contentType.startsWith("application/pdf;")) // charset=blabla
            return true;

        return false;
    }

    @Override
    public DetailsWithWords createDetails(CrawledDocument crawledDocument,
                                          LinkTexts linkTexts,
                                          DocumentClass documentClass)
            throws DisqualifiedException, URISyntaxException, IOException {

        String documentBody = crawledDocument.documentBody();

        if (languageFilter.isBlockedUnicodeRange(documentBody)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LANGUAGE);
        }

        final EdgeUrl url = new EdgeUrl(crawledDocument.url);


        DocumentLanguageData dld;
        try (var doc = PDDocument.load(crawledDocument.documentBodyBytes)) {
            String title = Objects.requireNonNullElse(doc.getDocumentInformation().getTitle(), "");

            dld = sentenceExtractorProvider.get().extractSentences(new PDFTextStripper().getText(doc), title);
        }

        checkDocumentLanguage(dld);

        documentLengthLogic.validateLength(dld, 1.0);

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
                pubDate.yearByte(), (int) -ret.quality, documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, linkTexts, url);

        var tagWords = new MetaTagsBuilder()
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

        return new DetailsWithWords(ret, words);
    }


}
