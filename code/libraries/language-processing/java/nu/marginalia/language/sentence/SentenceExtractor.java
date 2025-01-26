package nu.marginalia.language.sentence;

import com.github.datquocnguyen.RDRPOSTagger;
import com.google.inject.Inject;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.sentence.tag.HtmlStringTagger;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.language.sentence.tag.HtmlTaggedString;
import nu.marginalia.segmentation.NgramLexicon;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static NgramLexicon ngramLexicon = null;

    private final PorterStemmer porterStemmer = new PorterStemmer();
    private static final Logger logger = LoggerFactory.getLogger(SentenceExtractor.class);

    private static final SentencePreCleaner sentencePrecleaner = new SentencePreCleaner();

    /* Truncate sentences longer than this.  This is mostly a defense measure against malformed data
     * that might otherwise use an undue amount of processing power. 250 words is about 10X longer than
     * this comment. */
    static final int MAX_SENTENCE_LENGTH = 250;
    static final int MAX_SENTENCE_COUNT = 1000;

    @Inject
    public SentenceExtractor(LanguageModels models)
    {
        try (InputStream modelIn = new FileInputStream(models.openNLPSentenceDetectionData.toFile())) {
            var sentenceModel = new SentenceModel(modelIn);
            sentenceDetector = new SentenceDetectorME(sentenceModel);
        }
        catch (IOException ex) {
            sentenceDetector = null;
            logger.error("Could not initialize sentence detector", ex);
        }

        synchronized (SentenceExtractor.class) {
            if (ngramLexicon == null) {
                ngramLexicon = new NgramLexicon(models);
            }

            if (rdrposTagger == null) {
                try {
                    rdrposTagger = new RDRPOSTagger(models.posDict, models.posRules);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

    }

    public DocumentLanguageData extractSentences(Document doc) {
        final List<DocumentSentence> textSentences = new ArrayList<>();
        
        final List<HtmlTaggedString> taggedStrings = HtmlStringTagger.tagDocumentStrings(doc);

        final int totalTextLength = taggedStrings.stream().mapToInt(HtmlTaggedString::length).sum();
        final StringBuilder documentText = new StringBuilder(totalTextLength + taggedStrings.size());

        for (var taggedString : taggedStrings) {
            String text = taggedString.string();

            textSentences.addAll(
                    extractSentencesFromString(text, taggedString.tags())
            );

            if (documentText.isEmpty()) {
                documentText.append(text);
            }
            else {
                documentText.append(' ').append(text);
            }
        }

        return new DocumentLanguageData(textSentences, documentText.toString());
    }

    public DocumentLanguageData extractSentences(String text, String title) {
        var textSentences = extractSentencesFromString(text, EnumSet.noneOf(HtmlTag.class));
        var titleSentences = extractSentencesFromString(title.toLowerCase(), EnumSet.of(HtmlTag.TITLE));

        List<DocumentSentence> combined = new ArrayList<>(textSentences.size() + titleSentences.size());
        combined.addAll(titleSentences);
        combined.addAll(textSentences);

        return new DocumentLanguageData(
                combined,
                text);
    }

    public DocumentSentence extractSentence(String text, EnumSet<HtmlTag> htmlTags) {
        var wordsAndSeps = SentenceSegmentSplitter.splitSegment(text, MAX_SENTENCE_LENGTH);

        String[] words = wordsAndSeps.words();
        BitSet seps = wordsAndSeps.separators();
        String[] lc = new String[words.length];
        String[] stemmed = new String[words.length];

        BitSet isCapitalized = new BitSet(words.length);
        BitSet isAllCaps = new BitSet(words.length);

        for (int i = 0; i < words.length; i++) {
            lc[i] = stripPossessive(words[i].toLowerCase());

            if (words[i].length() > 0 && Character.isUpperCase(words[i].charAt(0))) {
                isCapitalized.set(i);
            }
            if (StringUtils.isAllUpperCase(words[i])) {
                isAllCaps.set(i);
            }

            try {
                stemmed[i] = porterStemmer.stem(lc[i]);
            }
            catch (Exception ex) {
                stemmed[i] = "NN"; // ???
            }
        }

        return new DocumentSentence(
                seps,
                lc,
                rdrposTagger.tagsForEnSentence(words),
                stemmed,
                htmlTags,
                isCapitalized,
                isAllCaps
        );
    }

    public List<DocumentSentence> extractSentencesFromString(String text, EnumSet<HtmlTag> htmlTags) {
        String[] sentences;

        // Safety net against malformed data DOS attacks,
        // found 5+ MB <p>-tags in the wild that just break
        // the sentence extractor causing it to stall forever.
        if (text.length() > 50_000) {
            // 50k chars can hold a small novel, let alone single html tags
            text = text.substring(0, 50_000);
        }

        // Normalize spaces
        text = normalizeSpaces(text);

        // Split into sentences

        try {
            sentences = sentenceDetector.sentDetect(text);
        }
        catch (Exception ex) {
            // shitty fallback logic
            sentences = StringUtils.split(text, '.');
        }

        sentences = sentencePrecleaner.clean(sentences);

        // Truncate the number of sentences if it exceeds the maximum, to avoid
        // excessive processing time on malformed data

        if (sentences.length > MAX_SENTENCE_COUNT) {
            sentences = Arrays.copyOf(sentences, MAX_SENTENCE_COUNT);
        }

        final boolean isNaturalLanguage = htmlTags.stream().noneMatch(tag -> tag.nonLanguage);

        List<DocumentSentence> ret = new ArrayList<>(sentences.length);

        if (isNaturalLanguage) {
            // Natural language text;  do POS tagging and stemming

            for (String sent : sentences) {
                var wordsAndSeps = SentenceSegmentSplitter.splitSegment(sent, MAX_SENTENCE_LENGTH);
                var tokens = wordsAndSeps.words();
                var separators = wordsAndSeps.separators();
                var posTags = rdrposTagger.tagsForEnSentence(tokens);
                var tokensLc = new String[tokens.length];
                var stemmed = new String[tokens.length];

                BitSet isCapitalized = new BitSet(tokens.length);
                BitSet isAllCaps = new BitSet(tokens.length);

                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i].length() > 0 && Character.isUpperCase(tokens[i].charAt(0))) {
                        isCapitalized.set(i);
                    }
                    if (StringUtils.isAllUpperCase(tokens[i])) {
                        isAllCaps.set(i);
                    }

                    var originalVal = tokens[i];
                    var newVal = stripPossessive(originalVal.toLowerCase());

                    if (Objects.equals(originalVal, newVal)) {
                        tokensLc[i] = originalVal;
                    } else {
                        tokensLc[i] = newVal;
                    }

                    try {
                        stemmed[i] = porterStemmer.stem(tokens[i]);
                    }
                    catch (Exception ex) {
                        stemmed[i] = "NN"; // ???
                    }
                }
                ret.add(new DocumentSentence(separators, tokensLc, posTags, stemmed, htmlTags, isCapitalized, isAllCaps));
            }
        }
        else {
            // non-language text, e.g. program code;  don't bother with POS tagging or stemming
            // as this is not likely to be useful

            for (String sent : sentences) {
                var wordsAndSeps = SentenceSegmentSplitter.splitSegment(sent, MAX_SENTENCE_LENGTH);
                var tokens = wordsAndSeps.words();
                var separators = wordsAndSeps.separators();
                var posTags = new String[tokens.length];
                Arrays.fill(posTags, "X"); // Placeholder POS tag
                var tokensLc = new String[tokens.length];
                var stemmed = new String[tokens.length];

                BitSet isCapitalized = new BitSet(tokens.length);
                BitSet isAllCaps = new BitSet(tokens.length);

                for (int i = 0; i < tokensLc.length; i++) {
                    var originalVal = tokens[i];

                    if (tokens[i].length() > 0 && Character.isUpperCase(tokens[i].charAt(0))) {
                        isCapitalized.set(i);
                    }
                    if (StringUtils.isAllUpperCase(tokens[i])) {
                        isAllCaps.set(i);
                    }

                    if (StringUtils.isAllLowerCase(originalVal)) {
                        tokensLc[i] = originalVal;
                    } else {
                        tokensLc[i] = originalVal.toLowerCase();
                    }
                    stemmed[i] = tokensLc[i]; // we don't stem non-language words
                }

                ret.add(new DocumentSentence(separators, tokensLc, posTags, stemmed, htmlTags, isAllCaps, isCapitalized));
            }

        }


        return ret;
    }


    public static String normalizeSpaces(String s) {
        if (s.indexOf('\t') >= 0) {
            s = s.replace('\t', ' ');
        }
        if (s.indexOf('\n') >= 0) {
            s = s.replace('\n', ' ');
        }
        return s;
    }

    public static String stripPossessive(String s) {
        int end = s.length();

        if (s.endsWith("'")) {
            return s.substring(0, end-1);
        }

        if (s.endsWith("'s") || s.endsWith("'S")) {
            return s.substring(0, end-2);
        }

        return s;
    }

}
