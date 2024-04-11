package nu.marginalia.functions.searchquery.query_parser;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import nu.marginalia.functions.searchquery.query_parser.model.QWord;
import nu.marginalia.functions.searchquery.query_parser.model.QWordGraph;
import nu.marginalia.functions.searchquery.query_parser.model.QWordPathsRenderer;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            this::createSegments
    );

    @Inject
    public QueryExpansion(TermFrequencyDict dict,
                          NgramLexicon lexicon
                          ) {
        this.dict = dict;
        this.lexicon = lexicon;
    }

    public String expandQuery(List<String> words) {

        QWordGraph graph = new QWordGraph(words);

        for (var strategy : expansionStrategies) {
            strategy.expand(graph);
        }

        return QWordPathsRenderer.render(graph);
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
     */
    public void createSegments(QWordGraph graph) {
        List<QWord> nodes = new ArrayList<>();

        for (var qw : graph) {
            nodes.add(qw);
        }

        String[] words = nodes.stream().map(QWord::stemmed).toArray(String[]::new);

        // Look for known segments within the query
        for (int length = 2; length < Math.min(10, words.length); length++) {
            for (var segment : lexicon.findSegments(length, words)) {
                int start = segment.start();
                int end = segment.start() + segment.length();
                var word = IntStream.range(start, end).mapToObj(nodes::get).map(QWord::word).collect(Collectors.joining("_"));

                graph.addVariantForSpan(nodes.get(start), nodes.get(end - 1), word);
            }
        }
    }

    public interface ExpansionStrategy {
        void expand(QWordGraph graph);
    }
}
