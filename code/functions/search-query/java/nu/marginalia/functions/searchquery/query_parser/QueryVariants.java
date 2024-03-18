package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.functions.searchquery.query_parser.token.Token;
import nu.marginalia.functions.searchquery.query_parser.token.TokenType;
import nu.marginalia.functions.searchquery.query_parser.variant.QueryVariant;
import nu.marginalia.functions.searchquery.query_parser.variant.QueryVariantSet;
import nu.marginalia.functions.searchquery.query_parser.variant.QueryWord;
import nu.marginalia.util.language.EnglishDictionary;
import nu.marginalia.LanguageModels;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.WordSpan;

import java.util.*;
import java.util.regex.Pattern;

public class QueryVariants {
    private final KeywordExtractor keywordExtractor;
    private final TermFrequencyDict dict;

    private final EnglishDictionary englishDictionary;
    private final ThreadLocal<SentenceExtractor> sentenceExtractor;

    public QueryVariants(LanguageModels lm,
                         TermFrequencyDict dict,
                         EnglishDictionary englishDictionary) {
        this.englishDictionary = englishDictionary;
        this.keywordExtractor = new KeywordExtractor();
        this.sentenceExtractor = ThreadLocal.withInitial(() -> new SentenceExtractor(lm));
        this.dict = dict;
    }



    public QueryVariantSet getQueryVariants(List<Token> query) {
        final JoinedQueryAndNonLiteralTokens joinedQuery = joinQuery(query);

        final TreeMap<Integer, List<WordSpan>> byStart = new TreeMap<>();

        var se = sentenceExtractor.get();
        var sentence = se.extractSentence(joinedQuery.joinedQuery);

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

        final List<List<QueryWord>> goodSpans = getWordSpans(byStart, sentence, livingSpans);

        List<List<String>> faithfulQueries = new ArrayList<>();
        List<List<String>> alternativeQueries = new ArrayList<>();

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

        returnValue.nonLiterals.addAll(joinedQuery.nonLiterals);

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

    private List<List<QueryWord>> getWordSpans(TreeMap<Integer, List<WordSpan>> byStart, DocumentSentence sentence, List<ArrayList<WordSpan>> livingSpans) {
        List<List<QueryWord>> goodSpans = new ArrayList<>();
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
                    var gs = new ArrayList<QueryWord>(span.size());
                    for (var s : span) {
                        gs.add(new QueryWord(sentence.constructStemmedWordFromSpan(s), sentence.constructWordFromSpan(s),
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


    private JoinedQueryAndNonLiteralTokens joinQuery(List<Token> query) {
        StringJoiner s = new StringJoiner(" ");
        List<Token> leftovers = new ArrayList<>(5);

        for (var t : query) {
            if (t.type == TokenType.LITERAL_TERM) {
                s.add(t.displayStr);
            }
            else {
                leftovers.add(t);
            }
        }

        return new JoinedQueryAndNonLiteralTokens(s.toString(), leftovers);
    }

    record JoinedQueryAndNonLiteralTokens(String joinedQuery, List<Token> nonLiterals) {}
}
