package nu.marginalia.converting.processor.plugin;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.converting.processor.plugin.specialization.DefaultSpecialization;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.filter.LanguageFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.HeadingAwarePDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;


public class PdfDocumentProcessorPlugin extends AbstractDocumentProcessorPlugin {

    private final int maxTitleLength;
    private final DocumentKeywordExtractor keywordExtractor;
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentLengthLogic documentLengthLogic;
    private final DefaultSpecialization defaultSpecialization;

    private static final Logger logger = LoggerFactory.getLogger(PdfDocumentProcessorPlugin.class);

    @Inject
    public PdfDocumentProcessorPlugin(@Named("max-title-length") Integer maxTitleLength,
                                      LanguageFilter languageFilter,
                                      ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                      DocumentKeywordExtractor keywordExtractor,
                                      DocumentLengthLogic documentLengthLogic,
                                      DefaultSpecialization defaultSpecialization)

    {
        super(languageFilter);
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.documentLengthLogic = documentLengthLogic;
        this.maxTitleLength = maxTitleLength;
        this.keywordExtractor = keywordExtractor;
        this.defaultSpecialization = defaultSpecialization;
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


        Document doc;
        try {
            doc = convertPdfToHtml(crawledDocument.documentBodyBytes);
        } catch (IOException e) {
            logger.error("Failed to convert PDF file {} - {}", url, e.getMessage());
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.ERROR);
        }

        DocumentLanguageData dld = sentenceExtractorProvider.get().extractSentences(doc);

        checkDocumentLanguage(dld);

        documentLengthLogic.validateLength(dld, 1.0);

        var ret = new ProcessedDocumentDetails();

        ret.length = documentBody.length();

        ret.format = DocumentFormat.PDF;
        ret.title = StringUtils.truncate(defaultSpecialization.getTitle(doc, dld, url.toString()), maxTitleLength);

        ret.quality = -5;

        ret.features = Set.of(HtmlFeature.PDF);
        ret.description = getDescription(doc);
        ret.hashCode = dld.localitySensitiveHashCode();

        final PubDate pubDate = new PubDate(LocalDate.ofYearDay(1993, 1));

        EnumSet<DocumentFlags> documentFlags = EnumSet.of(DocumentFlags.PdfFile);

        ret.metadata = new DocumentMetadata(
                documentLengthLogic.getEncodedAverageLength(dld),
                pubDate.yearByte(),
                (int) -ret.quality,
                documentFlags);

        DocumentKeywordsBuilder words = keywordExtractor.extractKeywords(dld, linkTexts, url);

        var tagWords = new MetaTagsBuilder()
                .addPubDate(pubDate)
                .addUrl(url)
                .addFeatures(ret.features)
                .addFormat(ret.format)
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

    private String getDescription(Document doc) {
        int cnt = 0;
        boolean useNext = false;
        for (var ptag : doc.getElementsByTag("p")) {
            String text = ptag.text();

            // Many academic documents have an abstract at the start of the document,
            // which makes a nice summary.  Though they tend to bleed into the text,
            // so we check for the word "Abstract" at the start of the paragraph.

            if (text.startsWith("Abstract ")) {
                return StringUtils.abbreviate(text.substring("Abstract ".length()), "...", 255);
            }
            else if (text.equals("Abstract")) {
                useNext = true;
            }
            else if (useNext) {
                return StringUtils.abbreviate(text, "...", 255);
            }

            if (++cnt > 15) { // Don't scan the entire document
                break;
            }
        }

        // Fall back to the default specialization
        return defaultSpecialization.getSummary(doc, Set.of());

    }

    /** Convert the provided PDF bytes into a HTML rendering that can be fed
     * to the HTML processor.
     */
    Document convertPdfToHtml(byte[] pdfBytes) throws IOException {
        try (var doc = Loader.loadPDF(pdfBytes)) {
            String docMetaTitle = Objects.requireNonNullElse(doc.getDocumentInformation().getTitle(), "");

            var stripper = new HeadingAwarePDFTextStripper();
            stripper.setStartPage(1);
            stripper.setSortByPosition(true);
            stripper.setWordSeparator(" ");

            // Increase the tolerance for line spacing to deal better with paragraphs.
            stripper.setDropThreshold(5f);

            stripper.setPageStart("<div>");
            stripper.setParagraphStart("<p>");
            stripper.setParagraphEnd("</p>\n");
            stripper.setPageEnd("</div>\n");
            stripper.setHeadingStart("<h1>");
            stripper.setHeadingEnd("</h1>\n");
            stripper.setLineSeparator("\n");

            String text = stripper.getText(doc);

            StringBuilder htmlBuilder = new StringBuilder(text.length() + 1024);
            htmlBuilder.append("<html><body>")
                    .append(text)
                    .append("</body></html>");

            var parsed = Jsoup.parse(htmlBuilder.toString());

            repairDOM(parsed);

            for (var heading : parsed.getElementsByTag("h1")) {
                String headingText = heading.text();
                if (headingText.length() > 2) {
                    parsed.title(headingText);
                    break;
                }
            }


            if (parsed.title().isEmpty()) {
                // Prefer setting the title to the first paragraph in the
                // document, as this is almost always correct.  Otherwise,
                // we fall back on the metadata title, which is almost always
                // useless

                var firstP = parsed.getElementsByTag("p").first();
                if (firstP != null) parsed.title(firstP.text());
                else parsed.title(docMetaTitle);
            }
            return parsed;
        }


    }

    /** Repair the DOM to remove some common issues with PDF conversion,
     * including empty paragraphs, and multiline headers that are split into multiple
     * conescutive h1 tags.
     */
    private void repairDOM(Document parsed) {

        // <p><h1>...</h1></p> -> <h1>...</h1>
        parsed.getElementsByTag("h1").forEach(h1 -> {
            var parent = h1.parent();
            if (parent == null || !"p".equals(parent.tagName())) {
                return;
            }

            if (parent.childrenSize() == 1) {
                parent.replaceWith(h1);
            }
        });

        // Remove empty <p> tags
        parsed.getElementsByTag("p").forEach(p -> {
            if (p.childrenSize() == 0 && !p.hasText()) {
                p.remove();
            }
        });

        // <h1>...</h1><h1>...</h1> -> <h1>...</h1>
        parsed.getElementsByTag("h1").forEach(h1 -> {
            var nextSibling = h1.nextElementSibling();
            if (nextSibling == null || !"h1".equals(nextSibling.tagName())) {
                return; // Short-circuit to avoid unnecessary work
            }

            StringJoiner joiner = new StringJoiner(" ");
            joiner.add(h1.text());

            for (var sibling : h1.nextElementSiblings()) {
                if (!"h1".equals(sibling.tagName()))
                    break;
                joiner.add(sibling.text());
                sibling.remove();
            }

            h1.text(joiner.toString());
        });

    }

}
