package nu.marginalia.index.results;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** <p>Selects an excerpt from the stored document text.</p>
 *
 * <p>The implementation follows the sentence-oriented model of the literature
 * (Tombros &amp; Sanderson 1998; Turpin et al., "Fast generation of result
 * snippets in web search", SIGIR 2007), scoring whole sentences and assembling
 * the snippet from the best ones.  Sentences are scored like miniature documents
 * with a BM25-style formula over the query terms, in the manner of Lucene's
 * PassageScorer, with a bonus for contiguous runs of query terms and a mild
 * preference for sentences early in the document.</p>
 */
public class SentenceSnippetExtractor {

    private static final int targetLength = 240;
    private static final String truncationMarker = "...";
    private static final String fragmentSeparator = " // ";

    private static final float K1 = 1.2f;
    private static final float B = 0.75f;
    private static final float AVG_LENGTH = 87f;

    private static final float RUN_BONUS = 0.2f;

    private final String text;
    private final List<Sentence> sentences;
    private final int tokenCount;

    @Nullable
    private final IntList excludedRangeStartEndPairs;

    public SentenceSnippetExtractor(String text, @Nullable IntList excludedRangeStartEndPairs) {
        this.text = text;
        this.sentences = splitSentences(text);
        this.tokenCount = sentences.isEmpty() ? 0 : sentences.getLast().lastTokenIdx() + 1;
        this.excludedRangeStartEndPairs = excludedRangeStartEndPairs;
    }

    @Nullable
    public String extract(IntList[] termPositions,
                          @Nullable float[] termIdfWeights,
                          @Nullable int[] termClasses)
    {
        if (tokenCount == 0) {
            return null;
        }

        assert termPositions.length < (1 << 16);

        LongArrayList matches = new LongArrayList();

        for (int termIdx = 0; termIdx < termPositions.length; termIdx++) {
            IntList positions = termPositions[termIdx];
            if (positions == null)
                continue;

            // Variants fold onto a shared representative termIdx and are scored
            // as the same term
            int matchTermIdx = termClasses != null ? termClasses[termIdx] : termIdx;

            // Zero-weighted terms (e.g. masked-out ngrams) neither score nor
            // count towards coverage
            if (termIdfWeights != null && termIdfWeights[matchTermIdx] <= 0)
                continue;

            for (int i = 0; i < positions.size(); i++) {
                int position = positions.getInt(i);

                if (position < 1)
                    continue;
                if (position > tokenCount)
                    continue;
                if (isPositionExcluded(position))
                    continue;

                matches.add(encodeMatch(matchTermIdx, position));
            }
        }

        // Terms do not obviously appear in the document, so no snippet is possible
        if (matches.isEmpty()) {
            return extractLead();
        }

        Arrays.sort(matches.elements(), 0, matches.size());

        SentenceSelection selection = scoreSentences(matches, termPositions.length, termIdfWeights);
        SentenceScore best = selection.best();

        // Assemble the snippet.  The primary fragment is the best sentence, when
        // it does not cover all matched query terms, the best sentence adding
        // coverage becomes a secondary fragment.

        SentenceScore secondary = null;
        for (int termIdx = 0; termIdx < Math.min(64, termPositions.length); termIdx++) {
            if ((best.termsMask() & (1L << termIdx)) != 0)
                continue;

            SentenceScore candidate = selection.bestPerTerm()[termIdx];
            if (candidate == null)
                continue;

            if (secondary == null || candidate.score() > secondary.score()) {
                secondary = candidate;
            }
        }

        // Order the fragments in document order

        TextFragment first;
        TextFragment second = null;

        if (secondary == null) {
            first = fragmentAround(best.firstMatchToken(), targetLength);
        }
        else {
            SentenceScore earlier = best.sentence().sentenceIdx() < secondary.sentence().sentenceIdx() ? best : secondary;
            SentenceScore later = earlier == best ? secondary : best;

            first = fragmentAround(earlier.firstMatchToken(), targetLength / 2);
            second = fragmentAround(later.firstMatchToken(), targetLength / 2);
        }

        extendFragments(first, second);

        return render(first, second);
    }

    /** Returns a query-independent lead excerpt of the text, the first sentences
     * of the document outside any excluded region, for use in place of the static
     * document summary when no query-biased snippet applies. */
    @Nullable
    public String extractLead() {
        int firstTokenIdx = firstDisplayableTokenIdx();
        if (firstTokenIdx < 0) {
            return null;
        }

        TextFragment fragment = fragmentAround(firstTokenIdx, targetLength);
        extendFragments(fragment, null);

        return render(fragment, null);
    }

    private int firstDisplayableTokenIdx() {
        for (int tokenIdx = 0; tokenIdx < tokenCount; tokenIdx++) {
            if (!isPositionExcluded(tokenIdx + 1))
                return tokenIdx;
        }

        return -1;
    }

    private boolean isPositionExcluded(int position) {
        if (excludedRangeStartEndPairs == null)
            return false;

        for (int i = 0; i + 1 < excludedRangeStartEndPairs.size(); i += 2) {
            if (position >= excludedRangeStartEndPairs.getInt(i) && position < excludedRangeStartEndPairs.getInt(i + 1))
                return true;
        }

        return false;
    }

    private SentenceSelection scoreSentences(LongArrayList matches,
                                             int numTerms,
                                             @Nullable float[] termIdfWeights) {
        SentenceScore best = null;
        SentenceScore[] bestPerTerm = new SentenceScore[numTerms];

        int[] tf = new int[numTerms];

        for (int begin = 0; begin < matches.size(); ) {
            int firstMatchToken = decodeMatchPosition(matches.getLong(begin)) - 1;
            Sentence sentence = sentenceContaining(firstMatchToken);

            int end = begin;
            while (end < matches.size() && decodeMatchPosition(matches.getLong(end)) - 1 <= sentence.lastTokenIdx()) {
                end++;
            }

            Arrays.fill(tf, 0);

            long termsMask = 0;
            int longestRun = 1;
            int run = 1;
            int prevPosition = -10;

            for (int i = begin; i < end; i++) {
                int position = decodeMatchPosition(matches.getLong(i));
                int termIdx = decodeMatchTermIdx(matches.getLong(i));

                tf[termIdx]++;

                if (termIdx < 64) {
                    termsMask |= 1L << termIdx;
                }

                if (position == prevPosition + 1) {
                    longestRun = Math.max(longestRun, ++run);
                }
                else if (position != prevPosition) {
                    run = 1;
                }
                prevPosition = position;
            }

            float score = 0;
            for (int termIdx = 0; termIdx < numTerms; termIdx++) {
                if (tf[termIdx] == 0)
                    continue;

                float weight = termIdfWeights != null ? termIdfWeights[termIdx] : 1.0f;
                float tfNorm = tf[termIdx] * (K1 + 1) / (tf[termIdx] + K1 * (1 - B + B * sentence.length() / AVG_LENGTH));

                score += weight * tfNorm;
            }

            score *= 1 + RUN_BONUS * (longestRun - 1);
            score *= 1 + 1 / (float) Math.log(AVG_LENGTH + sentence.charStart());

            SentenceScore candidate = new SentenceScore(sentence, score, firstMatchToken, termsMask);

            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }

            for (int i = begin; i < end; i++) {
                int termIdx = decodeMatchTermIdx(matches.getLong(i));
                if (bestPerTerm[termIdx] == null || candidate.score() > bestPerTerm[termIdx].score()) {
                    bestPerTerm[termIdx] = candidate;
                }
            }

            begin = end;
        }

        return new SentenceSelection(best, bestPerTerm);
    }


    private static long encodeMatch(int termIdx, int position) {
        return (long) position << 16 | termIdx;
    }
    private static int decodeMatchTermIdx(long match) {
        return (int) (match & ((1L << 16) - 1));
    }
    private static int decodeMatchPosition(long match) {
        return (int) (match >>> 16);
    }

    private record SentenceScore(Sentence sentence, float score, int firstMatchToken, long termsMask) {}

    private record SentenceSelection(SentenceScore best, SentenceScore[] bestPerTerm) {}

    private static class TextFragment {
        Sentence first;
        Sentence last;
        int charStart;
        int charEnd;

        TextFragment(Sentence sentence, int charStart, int charEnd) {
            this.first = sentence;
            this.last = sentence;
            this.charStart = charStart;
            this.charEnd = charEnd;
        }

        int length() {
            return charEnd - charStart;
        }
    }

    private TextFragment fragmentAround(int anchorToken, int budget) {
        Sentence sentence = sentenceContaining(anchorToken);

        if (sentence.length() <= budget) {
            return new TextFragment(sentence, sentence.charStart(), sentence.charEnd());
        }

        LongArrayList encodedTokenBounds = tokenize(sentence);
        int anchorIdx = anchorToken - sentence.firstTokenIdx();
        long encodedAnchor = encodedTokenBounds.get(anchorIdx);

        int charStart = decodeTokenEnd(encodedAnchor)
                - sentence.charStart() <= budget
                    ? sentence.charStart()
                    : decodeTokenStart(encodedAnchor);

        int charEnd = decodeTokenEnd(encodedAnchor);

        for (int i = anchorIdx + 1; i < encodedTokenBounds.size() && decodeTokenEnd(encodedTokenBounds.get(i)) - charStart <= budget; i++) {
            charEnd = decodeTokenEnd(encodedTokenBounds.get(i));
        }

        return new TextFragment(sentence, charStart, charEnd);
    }

    /** Spends any remaining character budget appending whole following sentences
     * to the fragments, preferring the primary (first) fragment */
    private void extendFragments(TextFragment first, @Nullable TextFragment second) {
        for (int i = 0; i < 100; i++) {
            int spent = first.length() + (second == null ? 0 : second.length());
            int remaining = targetLength - spent;
            if (remaining <= 0)
                return;

            if (!extendFragment(first, second, remaining)
                    && (second == null || !extendFragment(second, null, remaining))) {
                return;
            }
        }

        // Should never happen, but just in case there's a bug somewhere,
        // let's not spin forever
        throw new IllegalStateException("Could not extend fragments");
    }

    private boolean extendFragment(TextFragment fragment, @Nullable TextFragment next, int budget) {
        if (fragment.charEnd != fragment.last.charEnd())
            return false;  // fragment was cut mid-sentence, don't extend
        if (fragment.last.sentenceIdx() + 1 >= sentences.size())
            return false;

        Sentence nextSentence = sentences.get(fragment.last.sentenceIdx() + 1);

        // Don't grow into the next fragment or into excluded regions
        if (next != null && nextSentence.sentenceIdx() >= next.first.sentenceIdx())
            return false;
        if (isPositionExcluded(nextSentence.firstTokenIdx() + 1))
            return false;
        if (nextSentence.length() > budget)
            return false;

        fragment.last = nextSentence;
        fragment.charEnd = nextSentence.charEnd();
        return true;
    }

    private String render(TextFragment first, @Nullable TextFragment second) {
        StringBuilder snippet = new StringBuilder(targetLength + 16);

        // No leading truncation marker when everything skipped ahead of the
        // fragment is excluded content, e.g. a title leading the document text
        if (first.charStart > startOfToken(firstDisplayableTokenIdx())) {
            snippet.append(truncationMarker);
        }
        renderRange(first, snippet);

        if (second != null) {
            if (isContinuous(first, second)) {
                snippet.append(". ");
            }
            else {
                snippet.append(fragmentSeparator);
            }
            renderRange(second, snippet);
        }

        TextFragment last = second != null ? second : first;
        if (last.charEnd < sentences.getLast().charEnd()) {
            snippet.append(truncationMarker);
        }

        return snippet.toString();
    }

    private static boolean isContinuous(TextFragment first, TextFragment second) {
        return second.first.sentenceIdx() == first.last.sentenceIdx() + 1
                && second.charStart == second.first.charStart();
    }

    private void renderRange(TextFragment fragment, StringBuilder snippet) {
        // Sentence boundaries are represented as newlines in the stored reconstruction,
        // but here we render the boundaries with a period to keep adjacent sentences apart

        for (int i = fragment.charStart; i < fragment.charEnd; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                snippet.append(". ");
                while (i + 1 < fragment.charEnd && text.charAt(i + 1) == '\n') i++;
            }
            else {
                snippet.append(c);
            }
        }
    }

    private record Sentence(int sentenceIdx,
                            int firstTokenIdx,
                            int lastTokenIdx,
                            int charStart,
                            int charEnd)
    {
        int length() {
            return charEnd - charStart;
        }
    }


    private static List<Sentence> splitSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();

        int tokenCount = 0;
        final int length = text.length();

        for (int lineStart = 0; lineStart < length; ) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = length;
            }

            int charStart = lineStart;
            int charEnd = lineEnd;

            while (charStart < charEnd && isTokenBoundary(text.charAt(charStart))) charStart++;
            while (charEnd > charStart && isTokenBoundary(text.charAt(charEnd - 1))) charEnd--;

            int lineTokens = countTokens(text, charStart, charEnd);
            if (lineTokens > 0) {
                sentences.add(new Sentence(sentences.size(),
                        tokenCount, tokenCount + lineTokens - 1,
                        charStart, charEnd));
                tokenCount += lineTokens;
            }

            lineStart = lineEnd + 1;
        }

        return sentences;
    }

    private static int countTokens(String text, int charStart, int charEnd) {
        int count = 0;
        boolean previousBoundary = true;

        for (int i = charStart; i < charEnd; i++) {
            var isBoundary = isTokenBoundary(text.charAt(i));
            if (previousBoundary && isBoundary) {
                count++;
            }
            previousBoundary = isBoundary;
        }

        return count;
    }

    private static boolean isTokenBoundary(char c) {
        return c == ' ' || c == ',';
    }

    private Sentence sentenceContaining(int tokenIdx) {
        int low = 0;
        int high = sentences.size() - 1;

        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (sentences.get(mid).firstTokenIdx() <= tokenIdx) {
                low = mid;
            }
            else {
                high = mid - 1;
            }
        }

        return sentences.get(low);
    }


    private long encodeToken(int start, int end) {
        return (long) start << 32 | end;
    }
    private int decodeTokenStart(long token) {
        return (int) (token >>> 32);
    }
    private int decodeTokenEnd(long token) {
        return (int) token;
    }

    /** Return encoded token start,end pairs */
    private LongArrayList tokenize(Sentence sentence) {
        LongArrayList tokens = new LongArrayList();

        for (int pos = sentence.charStart(); pos < sentence.charEnd(); ) {
            while (pos < sentence.charEnd() && !isTokenBoundary(text.charAt(pos))) pos++;
            if (pos == sentence.charEnd())
                break;

            int start = pos;
            while (pos < sentence.charEnd() && !isTokenBoundary(text.charAt(pos))) pos++;
            tokens.add(encodeToken(start, pos));
        }

        return tokens;
    }

    private int startOfToken(int tokenIdx) {
        Sentence sentence = sentenceContaining(tokenIdx);
        return decodeTokenStart(tokenize(sentence).get(tokenIdx - sentence.firstTokenIdx()));
    }
}
