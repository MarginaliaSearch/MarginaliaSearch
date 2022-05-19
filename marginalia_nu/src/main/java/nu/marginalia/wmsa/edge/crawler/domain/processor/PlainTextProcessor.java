package nu.marginalia.wmsa.edge.crawler.domain.processor;

import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageContent;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageMetadata;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlainTextProcessor {

    private final DocumentKeywordExtractor keywordExtractor;
    private final SentenceExtractor sentenceExtractor;
    @Inject
    public PlainTextProcessor(DocumentKeywordExtractor keywordExtractor, SentenceExtractor sentenceExtractor) {
        this.keywordExtractor = keywordExtractor;
        this.sentenceExtractor = sentenceExtractor;
    }

    public Optional<EdgePageContent> parsePlainText(EdgeRawPageContents rawContents) {
        if (!isFileEndingAllowed(rawContents.url)) {
            return Optional.empty();
        }
        final String textData = rawContents.getData();
        final String[] textLines = textData.substring(0, Math.min(5000, textData.length())).split("\n");

        var dld = sentenceExtractor.extractSentences(textData);
        var keywords = keywordExtractor.extractKeywords(dld);
        keywords.get(IndexBlock.Meta).addJust("format:plain");
        keywords.get(IndexBlock.Words).addJust("format:plain");

        final var metadata = new EdgePageMetadata(0, 0, textData.length(),
                textData.length(), dld.totalNumWords(),
                rawContents.url.fileName(),
                getDescription(textLines),  0., 1,
                EdgeHtmlStandard.PLAIN);

        return Optional.of(new EdgePageContent(rawContents.url,
                keywords,
                Collections.emptyMap(),
                metadata,
                rawContents.getData().hashCode(),
                rawContents.ip));
    }

    private boolean isFileEndingAllowed(EdgeUrl url) {
        String urlString = url.toString().toLowerCase();
        if (urlString.endsWith(".txt")) {
            return true;
        }
        if (urlString.endsWith(".md")) {
            return true;
        }
        if (urlString.endsWith(".gmi")) {
            return true;
        }
        return false;
    }

    private String getDescription(String[] textLines) {
        return StringUtils.truncate(Arrays.stream(textLines).filter(Strings::isNotBlank).limit(10).collect(Collectors.joining("\n")), 200);
    }

}
