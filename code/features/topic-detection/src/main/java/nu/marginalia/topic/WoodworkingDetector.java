package nu.marginalia.topic;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import nu.marginalia.language.model.DocumentLanguageData;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class WoodworkingDetector {
    private static final int AVG_LENGTH = 1000;

    private final Map<String, Double> termValues = new HashMap<>();

    @Inject
    public WoodworkingDetector() {
        PorterStemmer ps = new PorterStemmer();

        termValues.put(ps.stemWord("shop"), -0.1);
        termValues.put(ps.stemWord("newsletter"), -0.1);
        termValues.put(ps.stemWord("cart"), -0.1);
        termValues.put(ps.stemWord("item"), -0.025);
        termValues.put(ps.stemWord("price"), -0.1);
        termValues.put(ps.stemWord("book"), -0.1);
        termValues.put(ps.stemWord("order"), -0.1);
        termValues.put(ps.stemWord("exhibition"), -0.1);

        // woodworking and joinery
        termValues.put(ps.stemWord("apse"), 0.01);
        termValues.put(ps.stemWord("baluster"), 0.01);
        termValues.put(ps.stemWord("beam"), 0.01);
        termValues.put(ps.stemWord("cornice"), 0.01);
        termValues.put(ps.stemWord("drill"), 0.01);
        termValues.put(ps.stemWord("nail"), 0.01);
        termValues.put(ps.stemWord("saw"), 0.01);
        termValues.put(ps.stemWord("hacksaw"), 0.01);
        termValues.put(ps.stemWord("bandsaw"), 0.01);
        termValues.put(ps.stemWord("whipsaw"), 0.01);
        termValues.put(ps.stemWord("gimlet"), 0.01);
        termValues.put(ps.stemWord("clamp"), 0.01);
        termValues.put(ps.stemWord("glue"), 0.01);
        termValues.put(ps.stemWord("cut"), 0.01);
        termValues.put(ps.stemWord("plane"), 0.01);
        termValues.put(ps.stemWord("sand"), 0.01);
        termValues.put(ps.stemWord("bevel"), 0.01);
        termValues.put(ps.stemWord("chamfer"), 0.01);
        termValues.put(ps.stemWord("dado"), 0.075);
        termValues.put(ps.stemWord("dowel"), 0.05);
        termValues.put(ps.stemWord("dovetail"), 0.05);
        termValues.put(ps.stemWord("joint"), 0.01);
        termValues.put(ps.stemWord("level"), 0.01);
        termValues.put(ps.stemWord("edge"), 0.01);
        termValues.put(ps.stemWord("face"), 0.01);
        termValues.put(ps.stemWord("fibreboard"), 0.01);
        termValues.put(ps.stemWord("fiberboard"), 0.01);
        termValues.put(ps.stemWord("battens"), 0.01);
        termValues.put(ps.stemWord("furring"), 0.01);
        termValues.put(ps.stemWord("glulam"), 0.025);
        termValues.put(ps.stemWord("hardboard"), 0.025);
        termValues.put(ps.stemWord("hardwood"), 0.01);
        termValues.put(ps.stemWord("jamb"), 0.015);
        termValues.put(ps.stemWord("kerf"), 0.025);
        termValues.put(ps.stemWord("lvl"), 0.025);
        termValues.put(ps.stemWord("laminated"), 0.01);
        termValues.put(ps.stemWord("lignin"), 0.01);
        termValues.put(ps.stemWord("mitre"), 0.01);
        termValues.put(ps.stemWord("mortise"), 0.015);
        termValues.put(ps.stemWord("mullion"), 0.01);
        termValues.put(ps.stemWord("newel"), 0.01);
        termValues.put(ps.stemWord("nogging"), 0.01);
        termValues.put(ps.stemWord("ogee"), 0.01);
        termValues.put(ps.stemWord("ogive"), 0.01);
        termValues.put(ps.stemWord("ovolo"), 0.01);
        termValues.put(ps.stemWord("drawknife"), 0.01);
        termValues.put(ps.stemWord("plywood"), 0.01);
        termValues.put(ps.stemWord("purlin"), 0.01);
        termValues.put(ps.stemWord("riser"), 0.01);
        termValues.put(ps.stemWord("sapwood"), 0.01);
        termValues.put(ps.stemWord("shingle"), 0.01);
        termValues.put(ps.stemWord("softwood"), 0.01);
        termValues.put(ps.stemWord("sapwood"), 0.01);
        termValues.put(ps.stemWord("stave"), 0.01);
        termValues.put(ps.stemWord("stopper"), 0.01);
        termValues.put(ps.stemWord("stud"), 0.01); // beep beep beep, huh, the stud detector seems to work just well :D
        termValues.put(ps.stemWord("transom"), 0.01);
        termValues.put(ps.stemWord("v-joint"), 0.015);
        termValues.put(ps.stemWord("veneer"), 0.01);
        termValues.put(ps.stemWord("quartersaw"), 0.015);
        termValues.put(ps.stemWord("screw"), 0.01);
        termValues.put(ps.stemWord("woodturning"), 0.01);

        termValues.put(ps.stemWord("pine"), 0.005);
        termValues.put(ps.stemWord("balsa"), 0.01);
        termValues.put(ps.stemWord("poplar"), 0.005);

        termValues.put(ps.stemWord("nut"), 0.01);
        termValues.put(ps.stemWord("bolt"), 0.01);
        termValues.put(ps.stemWord("tack"), 0.01);
        termValues.put(ps.stemWord("hinge"), 0.01);
        termValues.put(ps.stemWord("brass"), 0.01);
        termValues.put(ps.stemWord("fitting"), 0.01);

        termValues.put(ps.stemWord("diy"), 0.015);
        termValues.put(ps.stemWord("dozuki"), 0.01);
    }

    public double testP(DocumentLanguageData dld) {

        Map<String, Double> values = new HashMap<>();
        int count = 0;
        for (var sentence : dld.sentences) {

            for (var word : sentence) {
                count++;

                final String stemmed = word.stemmed();
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
