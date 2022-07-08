package nu.marginalia.wmsa.edge.converting.processor.logic;

import java.util.Collection;

public enum HtmlFeature {
    MEDIA(0, "special:media"),
    JS(1, "special:scripts"),
    AFFILIATE_LINK(2, "special:affiliate"),
    TRACKING(3, "special:tracking"),
    COOKIES(4, "special:cookies")
    ;

    public final int bit;
    private final String keyword;

    HtmlFeature(int bit, String keyword) {
        this.bit = bit;
        this.keyword = keyword;
    }

    public String getKeyword() {
        return keyword;
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
