package nu.marginalia.wmsa.edge.converting.processor.logic;

import java.util.Collection;

public enum HtmlFeature {
    MEDIA( "special:media"),
    JS("special:scripts"),
    AFFILIATE_LINK( "special:affiliate"),
    TRACKING("special:tracking"),
    COOKIES("special:cookies")
    ;

    private final String keyword;

    HtmlFeature(String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public static int encode(Collection<HtmlFeature> featuresAll) {
        int ret = 0;
        for (var feature : featuresAll) {
            ret |= (1 << (feature.ordinal()));
        }
        return ret;
    }

    public static boolean hasFeature(int value, HtmlFeature feature) {
        return (value & (1<< feature.ordinal())) != 0;
    }
}
