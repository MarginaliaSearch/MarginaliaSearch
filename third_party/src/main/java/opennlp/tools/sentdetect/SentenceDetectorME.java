/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package opennlp.tools.sentdetect;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

import java.io.IOException;
import java.util.*;

/**
 * A sentence detector for splitting up raw text into sentences.
 * <p>
 * A maximum entropy model is used to evaluate end-of-sentence characters in a
 * string to determine if they signify the end of a sentence.
 */
public class SentenceDetectorME implements SentenceDetector {

    /**
     * Constant indicates a sentence split.
     */
    public static final String SPLIT = "s";

    /**
     * Constant indicates no sentence split.
     */
    public static final String NO_SPLIT = "n";

    /**
     * The maximum entropy model to use to evaluate contexts.
     */
    private MaxentModel model;

    /**
     * The feature context generator.
     */
    private final SDContextGenerator cgen;

    /**
     * The {@link EndOfSentenceScanner} to use when scanning for end of sentence offsets.
     */
    private final EndOfSentenceScanner scanner;

    /**
     * The list of probabilities associated with each decision.
     */
    private List<Double> sentProbs = new ArrayList<>();

    protected boolean useTokenEnd;

    /**
     * Initializes the current instance.
     *
     * @param model the {@link SentenceModel}
     */
    public SentenceDetectorME(SentenceModel model) {
        SentenceDetectorFactory sdFactory = model.getFactory();
        this.model = model.getMaxentModel();
        cgen = sdFactory.getSDContextGenerator();
        scanner = sdFactory.getEndOfSentenceScanner();
        useTokenEnd = sdFactory.isUseTokenEnd();
    }

    /**
     * @deprecated Use a {@link SentenceDetectorFactory} to extend
     *             SentenceDetector functionality.
     */
    public SentenceDetectorME(SentenceModel model, Factory factory) {
        this.model = model.getMaxentModel();
        // if the model has custom EOS characters set, use this to get the context
        // generator and the EOS scanner; otherwise use language-specific defaults
        char[] customEOSCharacters = model.getEosCharacters();
        if (customEOSCharacters == null) {
            cgen = factory.createSentenceContextGenerator(model.getLanguage(),
                    getAbbreviations(model.getAbbreviations()));
            scanner = factory.createEndOfSentenceScanner(model.getLanguage());
        } else {
            cgen = factory.createSentenceContextGenerator(
                    getAbbreviations(model.getAbbreviations()), customEOSCharacters);
            scanner = factory.createEndOfSentenceScanner(customEOSCharacters);
        }
        useTokenEnd = model.useTokenEnd();
    }

    private static Set<String> getAbbreviations(Dictionary abbreviations) {
        if (abbreviations == null) {
            return Collections.emptySet();
        }
        return abbreviations.asStringSet();
    }

    /**
     * Detect sentences in a String.
     *
     * @param s  The string to be processed.
     *
     * @return   A string array containing individual sentences as elements.
     */
    public String[] sentDetect(String s) {
        Span[] spans = sentPosDetect(s);
        String[] sentences;
        if (spans.length != 0) {
            sentences = new String[spans.length];
            for (int si = 0; si < spans.length; si++) {
                sentences[si] = spans[si].getCoveredText(s).toString();
            }
        }
        else {
            sentences = new String[] {};
        }
        return sentences;
    }

    private int getFirstWS(String s, int pos) {
        while (pos < s.length() && !StringUtil.isWhitespace(s.charAt(pos)))
            pos++;
        return pos;
    }

    private int getFirstNonWS(String s, int pos) {
        while (pos < s.length() && StringUtil.isWhitespace(s.charAt(pos)))
            pos++;
        return pos;
    }

    /**
     * Detect the position of the first words of sentences in a String.
     *
     * @param s  The string to be processed.
     * @return   A integer array containing the positions of the end index of
     *          every sentence
     *
     */
    public Span[] sentPosDetect(String s) {
        sentProbs.clear();
        StringBuilder sb = new StringBuilder(s);
        List<Integer> enders = scanner.getPositions(s);
        List<Integer> positions = new ArrayList<>(enders.size());

        for (int i = 0, end = enders.size(), index = 0; i < end; i++) {
            int cint = enders.get(i);
            // skip over the leading parts of non-token final delimiters
            int fws = getFirstWS(s,cint + 1);
            if (i + 1 < end && enders.get(i + 1) < fws) {
                continue;
            }
            if (positions.size() > 0 && cint < positions.get(positions.size() - 1)) continue;

            double[] probs = model.eval(cgen.getContext(sb, cint));
            String bestOutcome = model.getBestOutcome(probs);

            if (bestOutcome.equals(SPLIT) && isAcceptableBreak(s, index, cint)) {
                if (index != cint) {
                    if (useTokenEnd) {
                        positions.add(getFirstNonWS(s, getFirstWS(s,cint + 1)));
                    }
                    else {
                        positions.add(getFirstNonWS(s, cint + 1));
                    }
                    sentProbs.add(probs[model.getIndex(bestOutcome)]);
                }

                index = cint + 1;
            }
        }

        int[] starts = new int[positions.size()];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = positions.get(i);
        }

        // string does not contain sentence end positions
        if (starts.length == 0) {

            // remove leading and trailing whitespace
            int start = 0;
            int end = s.length();

            while (start < s.length() && StringUtil.isWhitespace(s.charAt(start)))
                start++;

            while (end > 0 && StringUtil.isWhitespace(s.charAt(end - 1)))
                end--;

            if (end - start > 0) {
                sentProbs.add(1d);
                return new Span[] {new Span(start, end)};
            }
            else
                return new Span[0];
        }

        // Convert the sentence end indexes to spans

        boolean leftover = starts[starts.length - 1] != s.length();
        Span[] spans = new Span[leftover ? starts.length + 1 : starts.length];

        for (int si = 0; si < starts.length; si++) {
            int start;

            if (si == 0) {
                start = 0;
            }
            else {
                start = starts[si - 1];
            }

            // A span might contain only white spaces, in this case the length of
            // the span will be zero after trimming and should be ignored.
            Span span = new Span(start, starts[si]).trim(s);
            if (span.length() > 0) {
                spans[si] = span;
            }
            else {
                sentProbs.remove(si);
            }
        }

        if (leftover) {
            Span span = new Span(starts[starts.length - 1], s.length()).trim(s);
            if (span.length() > 0) {
                spans[spans.length - 1] = span;
                sentProbs.add(1d);
            }
        }
        /*
         * set the prob for each span
         */
        for (int i = 0; i < spans.length; i++) {
            double prob = sentProbs.get(i);
            spans[i] = new Span(spans[i], prob);

        }

        return spans;
    }

    /**
     * Returns the probabilities associated with the most recent
     * calls to sentDetect().
     *
     * @return probability for each sentence returned for the most recent
     *     call to sentDetect.  If not applicable an empty array is returned.
     */
    public double[] getSentenceProbabilities() {
        double[] sentProbArray = new double[sentProbs.size()];
        for (int i = 0; i < sentProbArray.length; i++) {
            sentProbArray[i] = sentProbs.get(i);
        }
        return sentProbArray;
    }

    /**
     * Allows subclasses to check an overzealous (read: poorly
     * trained) model from flagging obvious non-breaks as breaks based
     * on some boolean determination of a break's acceptability.
     *
     * <p>The implementation here always returns true, which means
     * that the MaxentModel's outcome is taken as is.</p>
     *
     * @param s the string in which the break occurred.
     * @param fromIndex the start of the segment currently being evaluated
     * @param candidateIndex the index of the candidate sentence ending
     * @return true if the break is acceptable
     */
    protected boolean isAcceptableBreak(String s, int fromIndex, int candidateIndex) {
        return true;
    }

    /**
     * @deprecated Use
     *             {@link #train(String, ObjectStream, SentenceDetectorFactory, TrainingParameters)}
     *             and pass in af {@link SentenceDetectorFactory}.
     */
    public static SentenceModel train(String languageCode,
                                      ObjectStream<SentenceSample> samples, boolean useTokenEnd,
                                      Dictionary abbreviations, TrainingParameters mlParams) throws IOException {
        SentenceDetectorFactory sdFactory = new SentenceDetectorFactory(
                languageCode, useTokenEnd, abbreviations, null);
        return train(languageCode, samples, sdFactory, mlParams);
    }

    public static SentenceModel train(String languageCode,
                                      ObjectStream<SentenceSample> samples, SentenceDetectorFactory sdFactory,
                                      TrainingParameters mlParams) throws IOException {

        Map<String, String> manifestInfoEntries = new HashMap<>();

        // TODO: Fix the EventStream to throw exceptions when training goes wrong
        ObjectStream<Event> eventStream = new SDEventStream(samples,
                sdFactory.getSDContextGenerator(), sdFactory.getEndOfSentenceScanner());

        EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);

        MaxentModel sentModel = trainer.train(eventStream);

        return new SentenceModel(languageCode, sentModel, manifestInfoEntries, sdFactory);
    }

    /**
     * @deprecated Use
     *             {@link #train(String, ObjectStream, SentenceDetectorFactory, TrainingParameters)}
     *             and pass in af {@link SentenceDetectorFactory}.
     */
    @Deprecated
    public static SentenceModel train(String languageCode, ObjectStream<SentenceSample> samples,
                                      boolean useTokenEnd, Dictionary abbreviations) throws IOException {
        return train(languageCode, samples, useTokenEnd, abbreviations,
                ModelUtil.createDefaultTrainingParameters());
    }
}
