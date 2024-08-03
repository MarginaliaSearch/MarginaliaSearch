package nu.marginalia.converting.processor.classifier.topic;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.language.model.DocumentLanguageData;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class AdHocDetector {
    private static final int AVG_LENGTH = 1000;

    private final Map<String, Double> termValues = new HashMap<>();

    public AdHocDetector(List<String> terms) {
        PorterStemmer ps = new PorterStemmer();

        for (String term : terms) {
            String[] parts = StringUtils.split(term, ' ');
            termValues.put(ps.stemWord(parts[0]), Double.parseDouble(parts[1]));
        }
    }

    public double testP(DocumentLanguageData dld) {

        Map<String, Double> values = new HashMap<>();
        int count = 0;
        for (var sentence : dld) {

            for (var stemmed : sentence.stemmedWords) {
                count++;

                final Double value = termValues.get(stemmed);

                if (value != null) {
                    values.merge(stemmed, value, (a,b) -> 0.5*a + b);
                }
            }

        }

        if (count == 0) return 0.;

        double lengthPenalty = sqrt(AVG_LENGTH)/sqrt(max(AVG_LENGTH, count));

        return values.values().stream().mapToDouble(Double::valueOf).sum() * lengthPenalty;
    }

}
