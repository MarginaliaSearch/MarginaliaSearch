package nu.marginalia.topic;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import nu.marginalia.language.model.DocumentLanguageData;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class TextileCraftDetector {
    private static final int AVG_LENGTH = 1000;

    private final Map<String, Double> termValues = new HashMap<>();

    @Inject
    public TextileCraftDetector() {
        PorterStemmer ps = new PorterStemmer();

        termValues.put(ps.stemWord("shop"), -0.1);
        termValues.put(ps.stemWord("newsletter"), -0.1);
        termValues.put(ps.stemWord("cart"), -0.1);
        termValues.put(ps.stemWord("item"), -0.025);
        termValues.put(ps.stemWord("price"), -0.1);
        termValues.put(ps.stemWord("book"), -0.1);
        termValues.put(ps.stemWord("order"), -0.1);
        termValues.put(ps.stemWord("exhibition"), -0.1);

        termValues.put(ps.stemWord("knit"), 0.05);
        termValues.put(ps.stemWord("stitch"), 0.05);
        termValues.put(ps.stemWord("yarn"), 0.05);
        termValues.put(ps.stemWord("crochet"), 0.05);
        termValues.put(ps.stemWord("ravelry"), 0.15);

        termValues.put(ps.stemWord("stockinette"), 0.075);
        termValues.put(ps.stemWord("purl"), 0.075);
        termValues.put(ps.stemWord("ksp"), 0.075);
        termValues.put(ps.stemWord("kwise"), 0.075);
        termValues.put(ps.stemWord("k2tog"), 0.075);
        termValues.put(ps.stemWord("k1b"), 0.075);
        termValues.put(ps.stemWord("psso"), 0.075);
        termValues.put(ps.stemWord("p2sso"), 0.075);
        termValues.put(ps.stemWord("pwise"), 0.075);
        termValues.put(ps.stemWord("yrn"), 0.075);
        termValues.put(ps.stemWord("yon"), 0.075);
        termValues.put(ps.stemWord("entrelac"), 0.075);
        termValues.put(ps.stemWord("thrum"), 0.075);
        termValues.put(ps.stemWord("bobbin"), 0.025);

        termValues.put(ps.stemWord("boucle"), 0.075);
        termValues.put(ps.stemWord("lopi"), 0.075);
        termValues.put(ps.stemWord("eyelash"), 0.01);
        termValues.put(ps.stemWord("variegated"), 0.075);

        termValues.put(ps.stemWord("serge"), 0.04);
        termValues.put(ps.stemWord("selvage"), 0.075);
        termValues.put(ps.stemWord("topstitch"), 0.075);

        termValues.put(ps.stemWord("gauge"), 0.01);
        termValues.put(ps.stemWord("design"), 0.01);
        termValues.put(ps.stemWord("pattern"), 0.01);
        termValues.put(ps.stemWord("layer"), 0.01);
        termValues.put(ps.stemWord("color"), 0.01);
        termValues.put(ps.stemWord("colour"), 0.01);
        termValues.put(ps.stemWord("chart"), 0.01);
        termValues.put(ps.stemWord("grid"), 0.01);
        termValues.put(ps.stemWord("wool"), 0.01);
        termValues.put(ps.stemWord("acrylic"), 0.01);
        termValues.put(ps.stemWord("loose"), 0.01);
        termValues.put(ps.stemWord("loop"), 0.01);
        termValues.put(ps.stemWord("needle"), 0.01);
        termValues.put(ps.stemWord("row"), 0.01);
        termValues.put(ps.stemWord("circular"), 0.01);
        termValues.put(ps.stemWord("sew"), 0.01);
        termValues.put(ps.stemWord("size"), 0.01);
        termValues.put(ps.stemWord("repeat"), 0.01);
        termValues.put(ps.stemWord("repetition"), 0.01);
        termValues.put(ps.stemWord("basketweave"), 0.01);
        termValues.put(ps.stemWord("weave"), 0.01);
        termValues.put(ps.stemWord("loom"), 0.01);
        termValues.put(ps.stemWord("warp"), 0.01);
        termValues.put(ps.stemWord("weft"), 0.01);
        termValues.put(ps.stemWord("shuttle"), 0.01);
        termValues.put(ps.stemWord("brioche"), 0.01);
        termValues.put(ps.stemWord("spool"), 0.01);
        termValues.put(ps.stemWord("hem"), 0.01);
        termValues.put(ps.stemWord("bodice"), 0.01);
        termValues.put(ps.stemWord("seam"), 0.01);
        termValues.put(ps.stemWord("allowance"), 0.01);
        termValues.put(ps.stemWord("crinoline"), 0.01);
        termValues.put(ps.stemWord("petticoat"), 0.01);
        termValues.put(ps.stemWord("armscye"), 0.01);
        termValues.put(ps.stemWord("baste"), 0.01);
        termValues.put(ps.stemWord("cord"), 0.01);
        termValues.put(ps.stemWord("darning"), 0.01);
        termValues.put(ps.stemWord("draping"), 0.01);
        termValues.put(ps.stemWord("embroider"), 0.01);
        termValues.put(ps.stemWord("eyelet"), 0.01);
        termValues.put(ps.stemWord("godet"), 0.01);
        termValues.put(ps.stemWord("gore"), 0.01);
        termValues.put(ps.stemWord("grain"), 0.01);
        termValues.put(ps.stemWord("jersey"), 0.01);
        termValues.put(ps.stemWord("lining"), 0.01);
        termValues.put(ps.stemWord("muslin"), 0.01);
        termValues.put(ps.stemWord("needlework"), 0.01);
        termValues.put(ps.stemWord("pleat"), 0.01);
        termValues.put(ps.stemWord("quilt"), 0.01);
        termValues.put(ps.stemWord("silk"), 0.01);

        termValues.put(ps.stemWord("sloper"), 0.01);
        termValues.put(ps.stemWord("surplice"), 0.01);
        termValues.put(ps.stemWord("thread"), 0.01);
        termValues.put(ps.stemWord("twill"), 0.01);

        termValues.put(ps.stemWord("ch"), 0.01);
        termValues.put(ps.stemWord("sp"), 0.01);
        termValues.put(ps.stemWord("sl"), 0.01);
        termValues.put(ps.stemWord("sc"), 0.01);
        termValues.put(ps.stemWord("ss"), 0.01);
        termValues.put(ps.stemWord("hdc"), 0.01);
        termValues.put(ps.stemWord("turn"), 0.01);
        termValues.put(ps.stemWord("skip"), 0.01);
        termValues.put(ps.stemWord("round"), 0.01);
        termValues.put(ps.stemWord("ring"), 0.01);

        termValues.put(ps.stemWord("sequin"), 0.01);
        termValues.put(ps.stemWord("bobble"), 0.01);
        termValues.put(ps.stemWord("puff"), 0.01);
        termValues.put(ps.stemWord("v-stitch"), 0.01);
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
