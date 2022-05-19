package nu.marginalia.wmsa.edge.model.crawl;

import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AllArgsConstructor @EqualsAndHashCode @Getter @Setter @ToString @With
public class EdgePageMetadata {
    public final int features;
    public final int scriptTags;
    public final int rawLength;
    public final int textBodyLength;
    public final int textDistinctWords;
    public final String title;
    public final String description;
    public final double smutCoefficient;
    public final int totalWords;
    public final EdgeHtmlStandard htmlStandard;
    private static final Logger logger = LoggerFactory.getLogger(EdgePageMetadata.class);
    private static EdgePageMetadata _empty
            = new EdgePageMetadata(0, 0,
            0,
            0,
            0,
            "",
            "",
            0.,
            1,
            EdgeHtmlStandard.UNKNOWN);
    public static EdgePageMetadata empty() {
        return _empty;
    }

    public double quality() {
        if (rawLength == 0 || textBodyLength == 0) {
            return -5.;
        }

/*        double dictionaryFactor = textDistinctWords / 10000.;
        if (dictionaryFactor <  0.1) {
            dictionaryFactor = 0;
        }*/

        return Math.log(textBodyLength / (double) rawLength)*htmlStandard.scale
                + htmlStandard.offset
                - scriptTags
              //  - dictionaryFactor
                - smutCoefficient;
    }
}
