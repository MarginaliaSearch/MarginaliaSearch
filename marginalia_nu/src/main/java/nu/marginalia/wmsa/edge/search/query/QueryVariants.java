package nu.marginalia.wmsa.edge.search.query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.KeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.DocumentSentence;
import nu.marginalia.util.language.processing.model.WordSpan;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import opennlp.tools.stemmer.PorterStemmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

@Singleton
public class QueryVariants {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final KeywordExtractor keywordExtractor;
    private final SentenceExtractor sentenceExtractor;
    private final TermFrequencyDict dict;
    private final PorterStemmer ps = new PorterStemmer();

    private final NGramBloomFilter nGramBloomFilter;
    private final EnglishDictionary englishDictionary;

    @Inject
    public QueryVariants(LanguageModels lm,
                         TermFrequencyDict dict,
                         NGramBloomFilter nGramBloomFilter,
                         EnglishDictionary englishDictionary) {
        this.nGramBloomFilter = nGramBloomFilter;
        this.englishDictionary = englishDictionary;
        this.keywordExtractor = new KeywordExtractor();
        this.sentenceExtractor = new SentenceExtractor(lm);
        this.dict = dict;
    }


    final Pattern numWordBoundary = Pattern.compile("[0-9][a-zA-Z]|[a-zA-Z][0-9]");
    final Pattern dashBoundary = Pattern.compile("-");

    @AllArgsConstructor
    private static class Word {
        public final String stemmed;
        public final String word;
        public final String wordOriginal;
    }

    @AllArgsConstructor @Getter @ToString @EqualsAndHashCode
    public static class QueryVariant {
        public final List<String> terms;
        public final double value;
    }

    @Getter @ToString
    public static class QueryVariantSet {
        final List<QueryVariant> faithful = new ArrayList<>();
        final List<QueryVariant> alternative = new ArrayList<>();

        public boolean isEmpty() {
            return faithful.isEmpty() && alternative.isEmpty();
        }
    }

    public QueryVariantSet getQueryVariants(List<Token> query) {
        final String queryAsString = joinQuery(query);

        final TreeMap<Integer, List<WordSpan>> byStart = new TreeMap<>();

        logger.debug("QAS: {}", queryAsString);

        var sentence = sentenceExtractor.extractSentence(queryAsString);

        for (int i = 0; i < sentence.posTags.length; i++) {
            if (sentence.posTags[i].startsWith("N") || sentence.posTags[i].startsWith("V")) {
                sentence.posTags[i] = "NNP";
            }
            else if ("JJ".equals(sentence.posTags[i]) || "CD".equals(sentence.posTags[i]) || sentence.posTags[i].startsWith("P")) {
                sentence.posTags[i] = "NNP";
                sentence.setIsStopWord(i, false);
            }
        }

        for (var kw : keywordExtractor.getKeywordsFromSentence(sentence)) {
            byStart.computeIfAbsent(kw.start, k -> new ArrayList<>()).add(kw);
        }

        final List<ArrayList<WordSpan>> livingSpans = new ArrayList<>();

        var first = byStart.firstEntry();
        if (first == null) {
            var span = new WordSpan(0, sentence.length());
            byStart.put(0, List.of(span));
        }
        else if (first.getKey() > 0) {
            List<WordSpan> elongatedFirstWords = new ArrayList<>(first.getValue().size());

            first.getValue().forEach(span -> {
                elongatedFirstWords.add(new WordSpan(0, span.start));
                elongatedFirstWords.add(new WordSpan(0, span.end));
            });

            byStart.put(0, elongatedFirstWords);
        }

        final List<List<Word>> goodSpans = getWordSpans(byStart, sentence, livingSpans);

        List<List<String>> faithfulQueries = new ArrayList<>();
        List<List<String>> alternativeQueries = new ArrayList<>();

        for (var ls : goodSpans) {
            faithfulQueries.addAll(createTokens(ls));
        }

        for (var span : goodSpans) {
            alternativeQueries.addAll(joinTerms(span));
        }

        for (var ls : goodSpans) {
            var last = ls.get(ls.size() - 1);

            if (!last.wordOriginal.isBlank() && !Character.isUpperCase(last.wordOriginal.charAt(0))) {
                var altLast = englishDictionary.getWordVariants(last.word);
                for (String s : altLast) {
                    List<String> newList = new ArrayList<>(ls.size());
                    for (int i = 0; i < ls.size() - 1; i++) {
                        newList.add(ls.get(i).word);
                    }
                    newList.add(s);
                    alternativeQueries.add(newList);
                }
            }

        }

        QueryVariantSet returnValue = new QueryVariantSet();

        returnValue.faithful.addAll(evaluateQueries(faithfulQueries));
        returnValue.alternative.addAll(evaluateQueries(alternativeQueries));

        returnValue.faithful.sort(Comparator.comparing(QueryVariant::getValue));
        returnValue.alternative.sort(Comparator.comparing(QueryVariant::getValue));

        return returnValue;
    }

    final Pattern underscore = Pattern.compile("_");

    private List<QueryVariant> evaluateQueries(List<List<String>> queryStrings) {
        Set<QueryVariant> variantsSet = new HashSet<>();
        List<QueryVariant> ret = new ArrayList<>();
        for (var lst : queryStrings) {
            double q = 0;
            for (var word : lst) {
                String[] parts = underscore.split(word);
                double qp = 0;
                for (String part : parts) {
                    qp += 1./(1+ dict.getTermFreq(part));
                }
                q += 1.0 / qp;
            }
            var qv = new QueryVariant(lst, q);
            if (variantsSet.add(qv)) {
                ret.add(qv);
            }
        }
        return ret;
    }

    private Collection<List<String>> createTokens(List<Word> ls) {
        List<String> asTokens = new ArrayList<>();
        List<List<String>> ret = new ArrayList<>();


        boolean dash = false;
        boolean num = false;

        for (var span : ls) {
            dash |= dashBoundary.matcher(span.word).find();
            num  |= numWordBoundary.matcher(span.word).find();
            if (ls.size() == 1 || !isOmittableWord(span.word)) {
                asTokens.add(span.word);
            }
        }
        ret.add(asTokens);

        if (dash) {
            ret.addAll(combineDashWords(ls));
        }

        if (num) {
            ret.addAll(splitWordNum(ls));
        }

        return ret;
    }

    private boolean isOmittableWord(String word) {
        return switch (word) {
            case "vs", "or", "and", "versus", "is", "the", "why", "when", "if", "who", "are", "am" -> true;
            default -> false;
        };
    }

    private Collection<? extends List<String>> splitWordNum(List<Word> ls) {
        List<String> asTokens2 = new ArrayList<>();

        boolean num = false;

        for (var span : ls) {
            var wordMatcher = numWordBoundary.matcher(span.word);
            var stemmedMatcher = numWordBoundary.matcher(span.stemmed);

            int ws = 0;
            int ss = 0;
            boolean didSplit = false;
            while (wordMatcher.find(ws) && stemmedMatcher.find(ss)) {
                ws = wordMatcher.start()+1;
                ss = stemmedMatcher.start()+1;
                if (nGramBloomFilter.isKnownNGram(splitAtNumBoundary(span.word, stemmedMatcher.start(), "_"))
                        || nGramBloomFilter.isKnownNGram(splitAtNumBoundary(span.word, stemmedMatcher.start(), "-")))
                {
                    String combined = splitAtNumBoundary(span.word, wordMatcher.start(), "_");
                    asTokens2.add(combined);
                    didSplit = true;
                    num = true;
                }
            }

            if (!didSplit) {
                asTokens2.add(span.word);
            }
        }

        if (num) {
            return List.of(asTokens2);
        }
        return Collections.emptyList();
    }

    private Collection<? extends List<String>> combineDashWords(List<Word> ls) {
        List<String> asTokens2 = new ArrayList<>();
        boolean dash = false;

        for (var span : ls) {
            var matcher = dashBoundary.matcher(span.word);
            if (matcher.find() && nGramBloomFilter.isKnownNGram(ps.stem(dashBoundary.matcher(span.word).replaceAll("")))) {
                dash = true;
                String combined = dashBoundary.matcher(span.word).replaceAll("");
                asTokens2.add(combined);
            }
            else {
                asTokens2.add(span.word);
            }
        }

        if (dash) {
            return List.of(asTokens2);
        }
        return Collections.emptyList();
    }

    private String splitAtNumBoundary(String in, int splitPoint, String joiner) {
        return in.substring(0, splitPoint+1) + joiner + in.substring(splitPoint+1);
    }

    private List<List<Word>> getWordSpans(TreeMap<Integer, List<WordSpan>> byStart, DocumentSentence sentence, List<ArrayList<WordSpan>> livingSpans) {
        List<List<Word>> goodSpans = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            var spans = byStart.get(i);


            if (spans == null )
                continue;

            for (var span : spans) {
                ArrayList<WordSpan> fragment = new ArrayList<>();
                fragment.add(span);
                livingSpans.add(fragment);
            }

            if (sentence.posTags[i].startsWith("N") || sentence.posTags[i].startsWith("V")) break;
        }


        while (!livingSpans.isEmpty()) {

            final List<ArrayList<WordSpan>> newLivingSpans = new ArrayList<>(livingSpans.size());

            for (var span : livingSpans) {
                int end = span.get(span.size()-1).end;

                if (end == sentence.length()) {
                    var gs = new ArrayList<Word>(span.size());
                    for (var s : span) {
                        gs.add(new Word(sentence.constructStemmedWordFromSpan(s), sentence.constructWordFromSpan(s),
                                s.size() == 1 ? sentence.words[s.start] : ""));
                    }
                    goodSpans.add(gs);
                }
                var nextWordsKey = byStart.ceilingKey(end);

                if (null == nextWordsKey)
                    continue;

                for (var next : byStart.get(nextWordsKey)) {
                    var newSpan = new ArrayList<WordSpan>(span.size() + 1);
                    newSpan.addAll(span);
                    newSpan.add(next);
                    newLivingSpans.add(newSpan);
                }
            }

            livingSpans.clear();
            livingSpans.addAll(newLivingSpans);
        }

        return goodSpans;
    }

    private List<List<String>> swapTerms(List<Word> span) {
        List<List<String>> ret = new ArrayList<>();

        for (int i = 0; i < span.size()-1; i++) {
            var a = span.get(i);
            var b = span.get(i+1);

            var stemmed = b.stemmed + "_" + a.stemmed;

            if (dict.getTermFreqStemmed(stemmed) > 0) {
                List<String> asTokens = new ArrayList<>();

                for (int j = 0; j < i; j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }
                {
                    var word = b.word + "_" + a.word;
                    asTokens.add(word);
                }
                for (int j = i+2; j < span.size(); j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }

                ret.add(asTokens);
            }
        }

        return ret;
    }


    private List<List<String>> joinTerms(List<Word> span) {
        List<List<String>> ret = new ArrayList<>();

        for (int i = 0; i < span.size()-1; i++) {
            var a = span.get(i);
            var b = span.get(i+1);

            var stemmed = ps.stem(a.word + b.word);

            double scoreCombo = dict.getTermFreqStemmed(stemmed);
            if (scoreCombo > 10000) {
                List<String> asTokens = new ArrayList<>();

                for (int j = 0; j < i; j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }
                {
                    var word = a.word + b.word;
                    asTokens.add(word);
                }
                for (int j = i+2; j < span.size(); j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }

                ret.add(asTokens);
            }
        }

        return ret;
    }

    private String joinQuery(List<Token> query) {
        StringJoiner s = new StringJoiner(" ");

        for (var t : query) {
            s.add(t.displayStr);
        }

        return s.toString();
    }
}
