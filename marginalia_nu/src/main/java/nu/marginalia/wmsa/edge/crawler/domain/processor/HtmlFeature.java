package nu.marginalia.wmsa.edge.crawler.domain.processor;

import java.util.Collection;

public enum HtmlFeature {
    MEDIA(0),
    JS(1),
    AFFILIATE_LINK(2),
    TRACKING(3),
    COOKIES(4)
    ;

    public int bit;

    HtmlFeature(int bit) {
        this.bit = bit;
    }

    public static int encode(Collection<HtmlFeature> featuresAll) {
        return featuresAll.stream().mapToInt(f -> 1 << f.bit).reduce(0, (l, r) -> (l|r));
    }
    public static boolean hasFeature(int value, HtmlFeature feature) {
        return (value & (1<< feature.bit)) != 0;
    }
    public static int addFeature(int value, HtmlFeature feature) {
        return (value | (1<< feature.bit));
    }
}
