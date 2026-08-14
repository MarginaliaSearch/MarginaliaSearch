package nu.marginalia.index.results;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import javax.annotation.Nullable;
import java.util.Arrays;

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

    @Nullable
    public static String extract(String text,
                                 IntList[] termPositions,
                                 @Nullable float[] termIdfWeights,
                                 @Nullable int[] termClasses,
                                 @Nullable IntList excludedRanges)
    {
        if (text.isEmpty()) {
            return null;
        }

        Tokens tokens = Tokens.of(text);
        if (tokens.count == 0) {
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
                if (position > tokens.count)
                    continue;
                if (inExcludedRange(excludedRanges, position))
                    continue;

                matches.add(encodeMatch(matchTermIdx, position));
            }
        }

        // Terms do not obviously appear in the document, so no snippet is possible
        if (matches.isEmpty()) {
            return extractLead(text, tokens, excludedRanges);
        }

        Arrays.sort(matches.elements(), 0, matches.size());

        SentenceSelection selection = scoreSentences(matches, tokens, termPositions.length, termIdfWeights);
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

        SentenceFragment first;
        SentenceFragment second = null;

        if (secondary == null) {
            first = new SentenceFragment(best);
        }
        else if (best.sentence() < secondary.sentence()) {
            first = new SentenceFragment(best);
            second = new SentenceFragment(secondary);
        }
        else {
            first = new SentenceFragment(secondary);
            second = new SentenceFragment(best);
        }

        // Align with sentences

        alignFragment(first, second == null ? targetLength : targetLength / 2, tokens);
        if (second != null) {
            alignFragment(second, targetLength / 2, tokens);
        }

        extendFragments(first, second, tokens, excludedRanges);

        return render(text, tokens, first, second, excludedRanges);
    }

    /** Returns a query-independent lead excerpt of the text, the first sentences
     * of the document outside any excluded region, for use in place of the static
     * document summary when no query-biased snippet applies. */
    @Nullable
    public static String extractLead(String text, @Nullable IntList excludedRanges) {
        if (text.isEmpty()) {
            return null;
        }

        Tokens tokens = Tokens.of(text);
        if (tokens.count == 0) {
            return null;
        }

        return extractLead(text, tokens, excludedRanges);
    }

    @Nullable
    private static String extractLead(String text, Tokens tokens, @Nullable IntList excludedRanges) {
        int firstToken = 0;
        while (firstToken < tokens.count && inExcludedRange(excludedRanges, firstToken + 1)) {
            firstToken++;
        }
        if (firstToken == tokens.count) {
            return null;
        }

        SentenceFragment fragment = new SentenceFragment(firstToken);
        alignFragment(fragment, targetLength, tokens);
        extendFragments(fragment, null, tokens, excludedRanges);

        return render(text, tokens, fragment, null, excludedRanges);
    }

    private static SentenceSelection scoreSentences(LongArrayList matches,
                                                    Tokens tokens,
                                                    int numTerms,
                                                    @Nullable float[] termIdfWeights) {
        SentenceScore best = null;
        SentenceScore[] bestPerTerm = new SentenceScore[numTerms];

        int[] tf = new int[numTerms];

        for (int begin = 0; begin < matches.size(); ) {
            int sentence = tokens.sentenceOfToken[decodeMatchPosition(matches.getLong(begin)) - 1];

            int end = begin;
            while (end < matches.size() && tokens.sentenceOfToken[decodeMatchPosition(matches.getLong(end)) - 1] == sentence) {
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

            int sentFirst = tokens.firstTokenOfSentence[sentence];
            int sentLength = tokens.ends[tokens.lastTokenOfSentence(sentence)] - tokens.starts[sentFirst];

            float score = 0;
            for (int termIdx = 0; termIdx < numTerms; termIdx++) {
                if (tf[termIdx] == 0)
                    continue;

                float weight = termIdfWeights != null ? termIdfWeights[termIdx] : 1.0f;
                float tfNorm = tf[termIdx] * (K1 + 1) / (tf[termIdx] + K1 * (1 - B + B * sentLength / AVG_LENGTH));

                score += weight * tfNorm;
            }

            score *= 1 + RUN_BONUS * (longestRun - 1);
            score *= 1 + 1 / (float) Math.log(AVG_LENGTH + tokens.starts[sentFirst]);

            SentenceScore candidate = new SentenceScore(sentence, score, decodeMatchPosition(matches.getLong(begin)) - 1, termsMask);

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

    private record SentenceScore(int sentence, float score, int firstMatchToken, long termsMask) {}

    private record SentenceSelection(SentenceScore best, SentenceScore[] bestPerTerm) {}

    private static class SentenceFragment {
        int firstToken;
        int lastToken;  // inclusive
        final int firstMatchToken;

        SentenceFragment(SentenceScore sentence) {
            this.firstMatchToken = sentence.firstMatchToken();
        }

        SentenceFragment(int firstMatchToken) {
            this.firstMatchToken = firstMatchToken;
        }
    }

    private static void alignFragment(SentenceFragment fragment, int budget, Tokens tokens) {
        int sentence = tokens.sentenceOfToken[fragment.firstMatchToken];

        int sentFirst = tokens.firstTokenOfSentence[sentence];
        int sentLast = tokens.lastTokenOfSentence(sentence);

        if (tokens.ends[sentLast] - tokens.starts[sentFirst] <= budget) {
            fragment.firstToken = sentFirst;
            fragment.lastToken = sentLast;
            return;
        }

        int start = sentFirst;
        if (tokens.ends[fragment.firstMatchToken] - tokens.starts[start] > budget) {
            start = fragment.firstMatchToken;
        }

        int end = start;
        while (end + 1 <= sentLast && tokens.ends[end + 1] - tokens.starts[start] <= budget) {
            end++;
        }

        fragment.firstToken = start;
        fragment.lastToken = end;
    }

    /** Spends any remaining character budget appending whole following sentences
     * to the fragments, preferring the primary (first) fragment */
    private static void extendFragments(SentenceFragment first,
                                        @Nullable SentenceFragment second,
                                        Tokens tokens,
                                        @Nullable IntList excludedRanges) {
        for (int i = 0; i < 100; i++) {
            int spent = fragmentLength(first, tokens) + (second == null ? 0 : fragmentLength(second, tokens));
            int remaining = targetLength - spent;
            if (remaining <= 0)
                return;

            if (!extendFragment(first, second, tokens, excludedRanges, remaining)
                    && (second == null || !extendFragment(second, null, tokens, excludedRanges, remaining))) {
                return;
            }
        }

        // Should never happen, but just in case there's a bug somewhere,
        // let's not spin forever
        throw new IllegalStateException("Could not extend fragments");
    }

    private static boolean extendFragment(SentenceFragment fragment,
                                          @Nullable SentenceFragment next,
                                          Tokens tokens,
                                          @Nullable IntList excludedRanges,
                                          int budget) {
        int lastSentence = tokens.sentenceOfToken[fragment.lastToken];

        if (fragment.lastToken != tokens.lastTokenOfSentence(lastSentence))
            return false;  // fragment was cut mid-sentence, don't extend
        if (lastSentence + 1 >= tokens.firstTokenOfSentence.length)
            return false;

        int nextFirst = tokens.firstTokenOfSentence[lastSentence + 1];
        int nextLast = tokens.lastTokenOfSentence(lastSentence + 1);

        // Don't grow into the next fragment or into excluded regions
        if (next != null && nextFirst >= next.firstToken)
            return false;
        if (inExcludedRange(excludedRanges, nextFirst + 1))
            return false;
        if (tokens.ends[nextLast] - tokens.starts[nextFirst] > budget)
            return false;

        fragment.lastToken = nextLast;
        return true;
    }

    private static int fragmentLength(SentenceFragment fragment, Tokens tokens) {
        return tokens.ends[fragment.lastToken] - tokens.starts[fragment.firstToken];
    }

    private static String render(String text,
                                 Tokens tokens,
                                 SentenceFragment first,
                                 @Nullable SentenceFragment second,
                                 @Nullable IntList excludedRanges) {
        StringBuilder snippet = new StringBuilder(targetLength + 16);

        // No leading truncation marker when everything skipped ahead of the
        // fragment is excluded content, e.g. a title leading the document text
        int firstDisplayable = 0;
        while (firstDisplayable < first.firstToken && inExcludedRange(excludedRanges, firstDisplayable + 1)) {
            firstDisplayable++;
        }

        if (first.firstToken > firstDisplayable) {
            snippet.append(truncationMarker);
        }
        renderRange(text, tokens, first, snippet);

        if (second != null) {
            if (isContinuous(first, second, tokens)) {
                snippet.append(". ");
            }
            else {
                snippet.append(fragmentSeparator);
            }
            renderRange(text, tokens, second, snippet);
        }

        SentenceFragment last = second != null ? second : first;
        if (last.lastToken + 1 < tokens.count) {
            snippet.append(truncationMarker);
        }

        return snippet.toString();
    }

    private static boolean isContinuous(SentenceFragment first, SentenceFragment second, Tokens tokens) {
        if (second.firstToken == first.lastToken + 1)
            return true;

        int secondSentence = tokens.sentenceOfToken[second.firstToken];

        return secondSentence == tokens.sentenceOfToken[first.lastToken] + 1
                && second.firstToken == tokens.firstTokenOfSentence[secondSentence];
    }

    private static void renderRange(String text, Tokens tokens, SentenceFragment fragment, StringBuilder snippet) {
        int from = tokens.starts[fragment.firstToken];
        int to = tokens.ends[fragment.lastToken];

        // Sentence boundaries are newlines in the stored reconstruction, and the
        // tokenizer strips terminal punctuation, so render the boundaries with a
        // period to keep adjacent sentences apart

        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                snippet.append(". ");
                while (i + 1 < to && text.charAt(i + 1) == '\n') i++;
            }
            else {
                snippet.append(c);
            }
        }
    }

    private static boolean inExcludedRange(@Nullable IntList ranges, int position) {
        if (ranges == null)
            return false;

        for (int i = 0; i + 1 < ranges.size(); i += 2) {
            if (position >= ranges.getInt(i) && position < ranges.getInt(i + 1))
                return true;
        }

        return false;
    }

    private static class Tokens {
        int count;
        int[] starts;
        int[] ends;
        int[] sentenceOfToken;
        int[] firstTokenOfSentence;

        public static Tokens of(String text) {
            final int length = text.length();
            int count = 0;

            for (int pos = 0; pos < length; ) {
                while (pos < length && isTokenBoundary(text.charAt(pos))) pos++;
                if (pos == length)
                    break;
                while (pos < length && !isTokenBoundary(text.charAt(pos))) pos++;
                count++;
            }

            Tokens ret = new Tokens();
            ret.count = count;
            ret.starts = new int[count];
            ret.ends = new int[count];
            ret.sentenceOfToken = new int[count];

            IntArrayList sentenceFirsts = new IntArrayList();

            int sentence = -1;
            boolean sentenceBoundaryPending = true;

            for (int pos = 0, token = 0; pos < length; ) {
                while (pos < length && isTokenBoundary(text.charAt(pos))) {
                    if (text.charAt(pos) == '\n')
                        sentenceBoundaryPending = true;
                    pos++;
                }
                if (pos == length)
                    break;

                if (sentenceBoundaryPending) {
                    sentence++;
                    sentenceFirsts.add(token);
                    sentenceBoundaryPending = false;
                }

                ret.starts[token] = pos;
                ret.sentenceOfToken[token] = sentence;
                while (pos < length && !isTokenBoundary(text.charAt(pos))) pos++;
                ret.ends[token++] = pos;
            }

            ret.firstTokenOfSentence = sentenceFirsts.toIntArray();

            return ret;
        }

        private int lastTokenOfSentence(int sentence) {
            if (sentence + 1 < firstTokenOfSentence.length)
                return firstTokenOfSentence[sentence + 1] - 1;
            return count - 1;
        }

        private static boolean isTokenBoundary(char c) {
            return c == ',' || Character.isWhitespace(c);
        }
    }
}
