package nu.marginalia.wmsa.edge.crawler.domain.processor;

import gnu.trove.list.array.TIntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.Pattern;

public class HtmlSummarizer {
    private static Pattern extendedJunk = Pattern.compile("[^a-zA-Z0-9]{4,}");

    private static final int MAX_CONSIDERABLE_SENTENCES = 200;
    private static final int MIN_SUMMARY_LENGTH = 20;
    private static final int MIN_TAG_LENGTH = 25;
    private static final int MAX_SUMMARY_LENGTH = 255;

    private final SentenceExtractor sentenceExtractor;


    public HtmlSummarizer(SentenceExtractor sentenceExtractor) {
        this.sentenceExtractor = sentenceExtractor;
    }

    public Optional<String> getSummary(Document parsed, Set<String> keywords) {
        List<String> candidates = extractCandidates(parsed);
        TIntArrayList scores = new TIntArrayList(candidates.size());

        for (String sentence : candidates) {
            scores.add(calculateScore(sentence, keywords));
        }

        String summary = constructSummary(candidates, scores);
        if (summary.isBlank() || summary.length() < MIN_SUMMARY_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(summary);
    }

    private String constructSummary(List<String> candidates, TIntArrayList scores) {
        int[] scoresReversed = getIndicesByScore(scores, candidates);
        TIntArrayList includedParts = new TIntArrayList();

        int length = 0;
        for (int i = 0; length < MAX_SUMMARY_LENGTH && i < scoresReversed.length; i++) {
            String sentence = candidates.get(scoresReversed[i]);
            length += sentence.length();
            includedParts.add(scoresReversed[i]);
        }
        includedParts.sort();

        StringBuilder summary = new StringBuilder();
        includedParts.forEach(i -> {
            var candidate = candidates.get(i).trim();

            summary.append(candidate);
            if (endsInLetterOrNumber(candidate.trim())) {
                summary.append(". ");
            }
            else if (!candidate.isEmpty() && !Character.isSpaceChar(candidate.charAt(candidate.length()-1))) {
                summary.append(" ");
            }
            return true;
        });

        return summary.toString();
    }

    private boolean endsInLetterOrNumber(String candidate) {
        if (candidate.isBlank()) return false;
        char lastChar = candidate.charAt(candidate.length()-1);

        return (Character.isAlphabetic(lastChar) || Character.isDigit(lastChar));
    }

    private int[] getIndicesByScore(TIntArrayList scores, List<String> candidates) {
        int[] scoresReversed = new int[scores.size()];
        for (int i = 0; i <scoresReversed.length; i++) scoresReversed[i] = i;
        IntArrays.quickSort(scoresReversed, (a,b) -> {
            int d = scores.get(b) - scores.get(a);
            if (d == 0) {
                return candidates.get(a).length() - candidates.get(b).length();
            }
            return d;
        });
        return scoresReversed;
    }

    private List<String> extractCandidates(Document parsed) {
        var clone = parsed.clone();
        clone.getElementsByTag("br").remove();

        List<String> ret = new ArrayList<>();

        for (var elem : clone.select("p,div,section,article")) {
            if (isCandidate(elem)) {
                ret.add(cleanText(elem.text()));
            }
            if (ret.size() > MAX_CONSIDERABLE_SENTENCES) {
                break;
            }
        };

        return ret;
    }
    private String cleanText(String text) {
        return extendedJunk.matcher(text).replaceAll(" ");

    }

    private int calculateScore(String sentence, Set<String> keywords) {
        int score = 0;

        final var data = sentenceExtractor.extractSentencesFromString(sentence);

        for (var s : data) {
            for (var word : s.wordsLowerCase) {
                if (keywords.contains(word)) {
                    score++;
                }
            }
        }

        return score;
    }

    private boolean isCandidate(Element elem) {
        if (elem.html().length() < MIN_TAG_LENGTH) {
            return false;
        }

        if (elem.childrenSize() > 3)
            return false;

        return elem.text().length() > 0.75*elem.html().length();
    }
}
