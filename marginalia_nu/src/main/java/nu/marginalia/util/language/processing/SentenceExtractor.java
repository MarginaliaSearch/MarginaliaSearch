package nu.marginalia.util.language.processing;

import com.github.datquocnguyen.RDRPOSTagger;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import nu.marginalia.util.language.processing.model.DocumentLanguageData;
import nu.marginalia.util.language.processing.model.DocumentSentence;
import nu.marginalia.util.language.processing.model.tag.WordSeparator;
import nu.marginalia.util.language.conf.LanguageModels;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static nu.marginalia.util.language.WordPatterns.*;

public class SentenceExtractor {

    private SentenceDetectorME sentenceDetector;
    private final RDRPOSTagger rdrposTagger;

    private final PorterStemmer porterStemmer = new PorterStemmer();
    private boolean legacyMode = false;
    private static final Logger logger = LoggerFactory.getLogger(SentenceExtractor.class);

    private static final HtmlTagCleaner tagCleaner = new HtmlTagCleaner();

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

        try {
            rdrposTagger = new RDRPOSTagger(models.posDict, models.posRules);
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public DocumentLanguageData extractSentences(Document doc) {
        final String text = asText(doc);
        final DocumentSentence[] textSentences = extractSentencesFromString(text);

        String title = doc.getElementsByTag("title").text() + " . "  +
                Optional.ofNullable(doc.getElementsByTag("h1").first()).map(Element::text).orElse("");

        if (title.trim().length() < 3) {
            title = Optional.ofNullable(doc.getElementsByTag("h2").first()).map(Element::text).orElse("");
        }

        if (title.trim().length() < 3 && textSentences.length > 0) {
            for (DocumentSentence textSentence : textSentences) {
                if (textSentence.length() > 0) {
                    title = textSentence.originalSentence.toLowerCase();
                    break;
                }
            }
        }

        TObjectIntHashMap<String> counts = calculateWordCounts(textSentences);
        var titleSentences = extractSentencesFromString(title.toLowerCase());
        return new DocumentLanguageData(textSentences, titleSentences, counts);
    }

    public DocumentLanguageData extractSentences(String text) {
        final DocumentSentence[] textSentences = extractSentencesFromString(text);

        String title = "";
        for (DocumentSentence textSentence : textSentences) {
            if (textSentence.length() > 0) {
                title = textSentence.originalSentence.toLowerCase();
                break;
            }
        }


        TObjectIntHashMap<String> counts = calculateWordCounts(textSentences);

        return new DocumentLanguageData(textSentences, extractSentencesFromString(title.toLowerCase()), counts);
    }


    public DocumentLanguageData extractSentences(String text, String title) {
        final DocumentSentence[] textSentences = extractSentencesFromString(text);

        TObjectIntHashMap<String> counts = calculateWordCounts(textSentences);

        return new DocumentLanguageData(textSentences, extractSentencesFromString(title.toLowerCase()), counts);
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

    private static final Pattern dotPattern = Pattern.compile("\\.+$");
    private static final Pattern splitPattern = Pattern.compile("( -|- |\\|)");
    private static final Pattern badCharPattern = Pattern.compile("([^_#@.a-zA-Z'+\\-0-9\\u00C0-\\u00D6\\u00D8-\\u00f6\\u00f8-\\u00ff]+)|(\\.(\\s+|$))");
    private static final Pattern possessivePattern = Pattern.compile("'(s)?$");

    public DocumentSentence extractSentence(String text) {
        var wordsAndSeps = splitSegment(text);

        var words = wordsAndSeps.words;
        var seps = wordsAndSeps.separators;
        var lc = toLc(wordsAndSeps.words);

        return new DocumentSentence(
            badCharPattern.matcher(text).replaceAll(" "), words, seps, lc, rdrposTagger.tagsForEnSentence(words), stemSentence(lc)
        );
    }

    public DocumentSentence[] extractSentencesFromString(String text) {
        String[] sentences;

        String textNormalizedSpaces = text.replaceAll("\\s", " ");
        try {
            sentences = sentenceDetector.sentDetect(textNormalizedSpaces);
        }
        catch (Exception ex) {
            sentences = textNormalizedSpaces.split("[.]");
        }

        if (sentences.length > 250) {
            sentences = Arrays.copyOf(sentences, 250);
        }

        sentences = Arrays.stream(sentences)
                .filter(s -> !s.isBlank())
                .flatMap(s -> Arrays.stream(splitPattern.split(s)))
                .toArray(String[]::new);

        final String[][] tokens = new String[sentences.length][];
        final int[][] separators = new int[sentences.length][];
        final String[][] posTags = new String[sentences.length][];
        final String[][] tokensLc = new String[sentences.length][];
        final String[][] stemmedWords = new String[sentences.length][];

        for (int i = 0; i < tokens.length; i++) {

            var wordsAndSeps = splitSegment(sentences[i]); //tokenizer.tokenize(sentences[i]);
            tokens[i] = wordsAndSeps.words;
            separators[i] = wordsAndSeps.separators;
            if (tokens[i].length > 250) {
                tokens[i] = Arrays.copyOf(tokens[i], 250);
                separators[i] = Arrays.copyOf(separators[i], 250);
            }
            for (int j = 0; j < tokens[i].length; j++) {
                tokens[i][j] = dotPattern.matcher(tokens[i][j]).replaceAll( "");
            }
        }

        for (int i = 0; i < tokens.length; i++) {
            posTags[i] = rdrposTagger.tagsForEnSentence(tokens[i]);
        }

        for (int i = 0; i < tokens.length; i++) {
            tokensLc[i] = toLc(tokens[i]);
        }

        for (int i = 0; i < tokens.length; i++) {
            stemmedWords[i] = stemSentence(tokensLc[i]);
        }

        DocumentSentence[] ret = new DocumentSentence[sentences.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = new DocumentSentence(badCharPattern.matcher(sentences[i]).replaceAll(" "), tokens[i], separators[i], tokensLc[i], posTags[i], stemmedWords[i]);
        }
        return ret;
    }

    private String[] stemSentence(String[] strings) {
        String[] stemmed = new String[strings.length];
        for (int i = 0; i < stemmed.length; i++) {
            var sent = possessivePattern.matcher(strings[i]).replaceAll("");
            try {
                stemmed[i] = porterStemmer.stem(sent);
            }
            catch (Exception ex) {
                stemmed[i] = "NN"; // ???
            }
        }
        return stemmed;
    }

    private String[] toLc(String[] words) {
        String[] lower = new String[words.length];
        for (int i = 0; i < lower.length; i++) {
            lower[i] = possessivePattern.matcher(words[i].toLowerCase()).replaceAll("");
        }
        return lower;
    }

    public String asText(Document dc) {

        tagCleaner.clean(dc);

        String text = dc.getElementsByTag("body").text();

        return text.substring(0, (int) (text.length()*0.95));
    }

    @AllArgsConstructor @Getter
    private static class WordsAndSeparators {
        String[] words;
        int[] separators;
    }

    private WordsAndSeparators splitSegment(String segment) {
        var matcher = wordBreakPattern.matcher(segment);

        List<String> words = new ArrayList<>(segment.length()/6);
        TIntArrayList separators = new TIntArrayList(segment.length()/6);

        int start = 0;
        int wordStart = 0;
        while (wordStart <= segment.length()) {
            if (!matcher.find(wordStart)) {
                words.add(segment.substring(wordStart));
                separators.add(WordSeparator.SPACE);
                break;
            }

            if (wordStart != matcher.start()) {
                words.add(segment.substring(wordStart, matcher.start()));
                separators.add(segment.substring(matcher.start(), matcher.end()).isBlank() ? WordSeparator.SPACE : WordSeparator.COMMA);
            }
            wordStart = matcher.end();
        }

        String[] parts = words.toArray(String[]::new);
        int length = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isBlank() || parts[i].length() >= MAX_WORD_LENGTH || characterNoisePredicate.test(parts[i])) {
                parts[i] = null;
            }
            else {
                length++;
            }
        }

        String[] ret = new String[length];
        int[] seps = new int[length];
        for (int i = 0, j=0; i < parts.length; i++) {
            if (parts[i] != null) {
                seps[j] = separators.getQuick(i);
                ret[j++] = parts[i];
            }
        }

        for (int i = 0; i < ret.length; i++) {
            if (ret[i].startsWith("'") && ret[i].length() > 1) { ret[i] = ret[i].substring(1); }
            if (ret[i].endsWith("'") && ret[i].length() > 1) { ret[i] = ret[i].substring(0, ret[i].length()-1); }
        }
        return new WordsAndSeparators(ret, seps);
    }


    public boolean isLegacyMode() {
        return legacyMode;
    }
    public void setLegacyMode(boolean legacyMode) {
        this.legacyMode = legacyMode;
    }

}
