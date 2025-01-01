package nu.marginalia.functions.searchquery.query_parser;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import nu.marginalia.functions.searchquery.query_parser.model.QWord;
import nu.marginalia.functions.searchquery.query_parser.model.QWordGraph;
import nu.marginalia.functions.searchquery.query_parser.model.QWordPathsRenderer;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Responsible for expanding a query, that is creating alternative branches of query execution
 *  to increase the number of results
 */
public class QueryExpansion {
    private static final PorterStemmer ps = new PorterStemmer();
    private final TermFrequencyDict dict;
    private final NgramLexicon lexicon;

    private final List<ExpansionStrategy> expansionStrategies = List.of(
            this::joinDashes,
            this::splitWordNum,
            this::joinTerms,
            this::categoryKeywords,
            this::ngramAll
    );

    @Inject
    public QueryExpansion(TermFrequencyDict dict,
                          NgramLexicon lexicon
                          ) {
        this.dict = dict;
        this.lexicon = lexicon;
    }

    public Expansion expandQuery(List<String> words) {

        QWordGraph graph = new QWordGraph(words);

        for (var strategy : expansionStrategies) {
            strategy.expand(graph);
        }

        List<List<String>> optionalPhraseConstraints = createSegments(graph);

        // also create a segmentation that is just the entire query
        List<String> fullPhraseConstraint = new ArrayList<> ();
        for (var qw : graph) {
            fullPhraseConstraint.add(qw.word());
        }

        var compiled = QWordPathsRenderer.render(graph);

        return new Expansion(compiled, optionalPhraseConstraints, fullPhraseConstraint);
    }

    private static final Pattern dashPattern = Pattern.compile("-");
    private static final Pattern numWordBoundary = Pattern.compile("[0-9][a-zA-Z]|[a-zA-Z][0-9]");

    // Turn 'lawn-chair' into 'lawnchair'
    public void joinDashes(QWordGraph graph) {
        for (var qw : graph) {
            if (qw.word().contains("-")) {
                var joined = StringUtils.join(dashPattern.split(qw.word()));
                graph.addVariant(qw, joined);
            }
        }
    }


    public void ngramAll(QWordGraph graph) {
        List<QWord> parts = new ArrayList<>();

        for (var qw : graph) {
            if (qw.isBeg() || qw.isEnd())
                continue;

            parts.add(qw);
        }

        if (parts.size() > 1) {
            graph.addVariantForSpan(parts.getFirst(), parts.getLast(),
                    parts.stream().map(QWord::word).collect(Collectors.joining("_")));
        }
    }

    // Turn 'MP3' into 'MP-3'
    public void splitWordNum(QWordGraph graph) {
        for (var qw : graph) {
            var matcher = numWordBoundary.matcher(qw.word());
            if (matcher.matches()) {
                var joined = StringUtils.join(dashPattern.split(qw.word()), '-');
                graph.addVariant(qw, joined);
            }
        }
    }

    // Category keyword substitution, e.g. guitar wiki -> guitar generator:wiki
    public void categoryKeywords(QWordGraph graph) {

        for (var qw : graph) {

            // Ensure we only perform the substitution on the last word in the query
            if (!graph.getNextOriginal(qw).getFirst().isEnd()) {
                continue;
            }

            switch (qw.word()) {
                case "recipe", "recipes" -> graph.addVariant(qw, "category:food");
                case "forum" -> graph.addVariant(qw, "generator:forum");
                case "wiki" -> graph.addVariant(qw, "generator:wiki");
            }
        }
    }

    // Turn 'lawn chair' into 'lawnchair'
    public void joinTerms(QWordGraph graph) {
        QWord prev = null;

        for (var qw : graph) {
            if (prev != null) {
                var joinedWord = prev.word() + qw.word();
                var joinedStemmed = ps.stemWord(joinedWord);

                var scoreA = dict.getTermFreqStemmed(prev.stemmed());
                var scoreB = dict.getTermFreqStemmed(qw.stemmed());

                var scoreCombo = dict.getTermFreqStemmed(joinedStemmed);

                if (scoreCombo > scoreA + scoreB || scoreCombo > 1000) {
                    graph.addVariantForSpan(prev, qw, joinedWord);
                }
            }

            prev = qw;
        }
    }

    /** Create an alternative interpretation of the query that replaces a sequence of words
     * with a word n-gram.  This makes it so that when possible, the order of words in the document
     * matches the order of the words in the query.
     *
     * The function modifies the graph in place, adding new variants to the graph; but also
     * returns a list of the new groupings that were added.
     */
    public List<List<String>> createSegments(QWordGraph graph)
    {
        List<QWord> nodes = new ArrayList<>();

        for (var qw : graph) {
            nodes.add(qw);
        }

        if (nodes.size() <= 1) {
            return List.of();
        }

        String[] words = nodes.stream().map(QWord::stemmed).toArray(String[]::new);

        // Grab all segments

        List<NgramLexicon.SentenceSegment> allSegments = new ArrayList<>();
        for (int length = 2; length < Math.min(10, words.length); length++) {
            allSegments.addAll(lexicon.findSegmentOffsets(length, words));
        }
        allSegments.sort(Comparator.comparing(NgramLexicon.SentenceSegment::start));

        Set<List<String>> constraints = new HashSet<>();

        Set<NgramLexicon.SentenceSegment> bestSegmentation =
                findBestSegmentation(allSegments);

        for (var segment : bestSegmentation) {

            int start = segment.start();
            int end = segment.start() + segment.length();

            List<String> components = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                components.add(nodes.get(i).word());
            }
            constraints.add(components);

            // Create an n-gram search term for the segment
            String word = String.join("_", components);
            graph.addVariantForSpan(nodes.get(start), nodes.get(end - 1), word);
        }

        return new ArrayList<>(constraints);
    }

    private Set<NgramLexicon.SentenceSegment> findBestSegmentation(List<NgramLexicon.SentenceSegment> allSegments) {
        Set<NgramLexicon.SentenceSegment> bestSet = Set.of();
        double bestScore = Double.MIN_VALUE;

        for (int i = 0; i < allSegments.size(); i++) {
            Set<NgramLexicon.SentenceSegment> parts = new HashSet<>();
            parts.add(allSegments.get(i));

            outer:
            for (int j = i+1; j < allSegments.size(); j++) {
                var candidate = allSegments.get(j);
                for (var part : parts) {
                    if (part.overlaps(candidate)) {
                        continue outer;
                    }
                }
                parts.add(candidate);
            }

            double score = 0.;
            for (var part : parts) {
                // |s|^|s|-normalization per M Hagen et al
                double normFactor = Math.pow(part.length(), part.length());

                score += normFactor * part.count();
            }

            if (bestScore < score) {
                bestScore = score;
                bestSet = parts;
            }
        }

        return bestSet;
    }

    public interface ExpansionStrategy {
        void expand(QWordGraph graph);
    }

    public record Expansion(String compiledQuery, List<List<String>> optionalPharseConstraints, List<String> fullPhraseConstraint) {}
}
