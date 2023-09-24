package nu.marginalia.language.sentence;

import com.github.datquocnguyen.RDRPOSTagger;
import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**  This class still isn't thread safe!!  If you use it concurrently, it won't throw exceptions,
 * it will just haunt your code and cause unpredictable mysterious errors.
 *
 * Use {@link ThreadLocalSentenceExtractorProvider} instead to avoid falling into the twilight zone!
 */
public class SentenceExtractor {

    private SentenceDetectorME sentenceDetector;
    private static RDRPOSTagger rdrposTagger;

    private final PorterStemmer porterStemmer = new PorterStemmer();
    private static final Logger logger = LoggerFactory.getLogger(SentenceExtractor.class);

    private static final SentenceExtractorHtmlTagCleaner tagCleaner = new SentenceExtractorHtmlTagCleaner();
    private static final SentencePreCleaner sentencePrecleaner = new SentencePreCleaner();

    /* Truncate sentences longer than this.  This is mostly a defense measure against malformed data
     * that might otherwise use an undue amount of processing power. 250 words is about 10X longer than
     * this comment. */
    private static final int MAX_SENTENCE_LENGTH = 250;

    @SneakyThrows @Inject
    public SentenceExtractor(LanguageModels models) {
        try (InputStream modelIn = new FileInputStream(models.openNLPSentenceDetectionData.toFile())) {
            var sentenceModel = new SentenceModel(modelIn);
            sentenceDetector = new SentenceDetectorME(sentenceModel);
        }
        catch (IOException ex) {
            sentenceDetector = null;
            logger.error("Could not initialize sentence detector", ex);
        }

        synchronized (RDRPOSTagger.class) {
            try {
                rdrposTagger = new RDRPOSTagger(models.posDict, models.posRules);
            }
            catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

    }

    public DocumentLanguageData extractSentences(Document doc) {
        var clone = doc.clone();
        tagCleaner.clean(clone);

        final String text = asText(clone);
        final DocumentSentence[] textSentences = extractSentencesFromString(text);

        String title = getTitle(clone, textSentences);

        TObjectIntHashMap<String> counts = calculateWordCounts(textSentences);
        var titleSentences = extractSentencesFromString(title.toLowerCase());
        return new DocumentLanguageData(textSentences, titleSentences, counts, text);
    }

    public DocumentLanguageData extractSentences(String text, String title) {
        final DocumentSentence[] textSentences = extractSentencesFromString(text);

        TObjectIntHashMap<String> counts = calculateWordCounts(textSentences);
        var titleSentences = extractSentencesFromString(title.toLowerCase());
        return new DocumentLanguageData(textSentences, titleSentences, counts, text);
    }

    private String getTitle(Document doc, DocumentSentence[] textSentences) {
        String title = doc.getElementsByTag("title").text() + " . "  +
                Optional.ofNullable(doc.getElementsByTag("h1").first()).map(Element::text).orElse("");

        if (title.trim().length() < 3) {
            title = doc.getElementsByTag("h2").text();
        }

        if (title.trim().length() < 3 && textSentences.length > 0) {
            for (DocumentSentence textSentence : textSentences) {
                if (textSentence.length() > 0) {
                    title = textSentence.originalSentence.toLowerCase();
                    break;
                }
            }
        }

        return title;
    }


    @NotNull
    private TObjectIntHashMap<String> calculateWordCounts(DocumentSentence[] textSentences) {
        TObjectIntHashMap<String> counts = new TObjectIntHashMap<>(textSentences.length*10, 0.5f, 0);

        for (var sent : textSentences) {
            for (var word : sent.stemmedWords) {
                counts.adjustOrPutValue(word, 1, 1);
            }
        }
        return counts;
    }

    public DocumentSentence extractSentence(String text) {
        var wordsAndSeps = SentenceSegmentSplitter.splitSegment(text);

        var words = wordsAndSeps.words;
        var seps = wordsAndSeps.separators;
        var lc = SentenceExtractorStringUtils.toLowerCaseStripPossessive(wordsAndSeps.words);

        return new DocumentSentence(
            SentenceExtractorStringUtils.sanitizeString(text), words, seps, lc, rdrposTagger.tagsForEnSentence(words), stemSentence(lc)
        );
    }

    public DocumentSentence[] extractSentencesFromString(String text) {
        String[] sentences;

        String textNormalizedSpaces = SentenceExtractorStringUtils.normalizeSpaces(text);
        try {
            sentences = sentenceDetector.sentDetect(textNormalizedSpaces);
        }
        catch (Exception ex) {
            // shitty fallback logic
            sentences = StringUtils.split(textNormalizedSpaces, '.');
        }

        sentences = sentencePrecleaner.clean(sentences);

        final String[][] tokens = new String[sentences.length][];
        final int[][] separators = new int[sentences.length][];
        final String[][] posTags = new String[sentences.length][];
        final String[][] tokensLc = new String[sentences.length][];
        final String[][] stemmedWords = new String[sentences.length][];

        for (int i = 0; i < tokens.length; i++) {

            var wordsAndSeps = SentenceSegmentSplitter.splitSegment(sentences[i]);
            tokens[i] = wordsAndSeps.words;
            separators[i] = wordsAndSeps.separators;

            if (tokens[i].length > MAX_SENTENCE_LENGTH) {
                tokens[i] = Arrays.copyOf(tokens[i], MAX_SENTENCE_LENGTH);
                separators[i] = Arrays.copyOf(separators[i], MAX_SENTENCE_LENGTH);
            }

            for (int j = 0; j < tokens[i].length; j++) {
                while (tokens[i][j].endsWith(".")) {
                    tokens[i][j] = StringUtils.removeEnd(tokens[i][j], ".");
                }
            }
        }

        for (int i = 0; i < tokens.length; i++) {
            posTags[i] = rdrposTagger.tagsForEnSentence(tokens[i]);
        }

        for (int i = 0; i < tokens.length; i++) {
            tokensLc[i] = SentenceExtractorStringUtils.toLowerCaseStripPossessive(tokens[i]);
        }

        for (int i = 0; i < tokens.length; i++) {
            stemmedWords[i] = stemSentence(tokensLc[i]);
        }

        DocumentSentence[] ret = new DocumentSentence[sentences.length];
        for (int i = 0; i < ret.length; i++) {
            String fullString;

            if (i == 0) {
                fullString = SentenceExtractorStringUtils.sanitizeString(sentences[i]);
            }
            else {
                fullString = "";
            }

            ret[i] = new DocumentSentence(fullString, tokens[i], separators[i], tokensLc[i], posTags[i], stemmedWords[i]);
        }
        return ret;
    }

    private String[] stemSentence(String[] strings) {
        String[] stemmed = new String[strings.length];
        for (int i = 0; i < stemmed.length; i++) {
            var sent = SentenceExtractorStringUtils.stripPossessive(strings[i]);
            try {
                stemmed[i] = porterStemmer.stem(sent);
            }
            catch (Exception ex) {
                stemmed[i] = "NN"; // ???
            }
        }
        return stemmed;
    }

    public String asText(Document dc) {
        String text = dc.getElementsByTag("body").text();

        return text.substring(0, (int) (text.length()*0.95));
    }



}
