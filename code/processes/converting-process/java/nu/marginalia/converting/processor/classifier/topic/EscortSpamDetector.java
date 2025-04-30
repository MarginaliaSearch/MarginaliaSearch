package nu.marginalia.converting.processor.classifier.topic;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Basic spam detector for escort ads.
 * <p></p>
 * Tries to differentiate between escorts (callgirls) and escorts (warships)
 * and the ford escort.
 */
public class EscortSpamDetector {

    private final Map<String, Double> sexyValues = new HashMap<>();
    private final Map<String, Double> escortValues = new HashMap<>();
    private final Map<String, Double> navyValues = new HashMap<>();
    private final Map<String, Double> carValues = new HashMap<>();
    private final List<String[]> callgirlPhrases = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(EscortSpamDetector.class);
    private static final Marker marker = MarkerFactory.getMarker("FILTER");

    PorterStemmer ps = new PorterStemmer();

    @Inject
    public EscortSpamDetector() {

        register(sexyValues, "sexy", 0.5);
        register(sexyValues, "hot", 0.1);
        register(sexyValues, "girl", 0.3);
        register(sexyValues, "massage", 0.3);
        register(sexyValues, "adult", 0.3);
        register(sexyValues, "companion", 0.3);
        register(sexyValues, "date", 0.1);
        register(sexyValues, "callgirl", 0.5); // Note callgirl will raise escortValues too

        register(escortValues, "escort", 1);
        register(escortValues, "callgirl", 1);

        register(navyValues, "navy", 0.1);
        register(navyValues, "fleet", 0.2);
        register(navyValues, "maritime", 0.3);
        register(navyValues, "warship", 0.5);
        register(navyValues, "cruiser", 0.5);
        register(navyValues, "carrier", 0.3);
        register(navyValues, "destroyer", 0.3);

        register(carValues, "ford", 0.3);
        register(carValues, "vehicle", 0.3);
        register(carValues, "sedan", 0.3);
        register(carValues, "hatchback", 0.3);
        register(carValues, "transmission", 0.3);
        register(carValues, "exhaust", 0.3);
        register(carValues, "fuel", 0.3);

        addCallgirlPhrase("call", "girl");
        addCallgirlPhrase("escort", "service");
    }

    private void register(Map<String, Double> map, String word, double value) {
        String stemmed = ps.stemWord(word);
        map.put(stemmed, value);
    }

    private void addCallgirlPhrase(String word1, String word2) {
        String stemmed1 = ps.stemWord(word1);
        String stemmed2 = ps.stemWord(word2);
        callgirlPhrases.add(new String[] { stemmed1, stemmed2 });
    }

    public boolean test(DocumentLanguageData dld, EdgeUrl url) {

        double sexyP = 0.0;
        double escortP = 0.0;
        double navyP = 0.0;
        double carP = 0.0;

        int count = 0;

        for (var sentence : dld) {

            String prev = "";
            for (var stemmed : sentence.stemmedWords) {
                count++;

                sexyP += sexyValues.getOrDefault(stemmed, 0.0);
                escortP += escortValues.getOrDefault(stemmed, 0.0);
                navyP += navyValues.getOrDefault(stemmed, 0.0);
                carP += carValues.getOrDefault(stemmed, 0.0);

                for (var phrase : callgirlPhrases) {
                    if (prev.equals(phrase[0]) && stemmed.equals(phrase[1])) {
                        escortP += 0.5;
                        sexyP += 0.5;
                    }
                }

                prev = stemmed;
            }

        }

        if (count == 0 || escortP <= 0) return false;

        boolean is = sexyP > navyP + carP;
        if (is) {
            logger.info(marker, "Escort spam identified in {}", url);
        }
        return is;
    }

}
