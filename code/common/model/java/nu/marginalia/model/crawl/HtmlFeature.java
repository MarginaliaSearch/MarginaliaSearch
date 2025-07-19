package nu.marginalia.model.crawl;

import java.util.Collection;

public enum HtmlFeature {
    // Note, the first 32 of these features are bit encoded in the database
    // so be sure to keep anything that's potentially important toward the top
    // of the list; but adding new values will shift the encoded values and break
    // binary compatibility!  Scroll down for a marker where you should add new values
    // if they need to be accessible from IndexResultScoreCalculator!

    MEDIA( "special:media"),
    JS("special:scripts"),
    AFFILIATE_LINK( "special:affiliate"),
    TRACKING("special:tracking"),
    TRACKING_ADTECH("special:adtech"),

    KEBAB_CASE_URL("special:kcurl"), // https://www.example.com/urls-that-look-like-this/
    LONG_URL("special:longurl"),

    CLOUDFLARE_FEATURE("special:cloudflare"),
    CDN_FEATURE("special:cdn"),

    VIEWPORT("special:viewport"),

    COOKIES("special:cookies"),
    CATEGORY_FOOD("category:food"),
    ADVERTISEMENT("special:ads"),
    CATEGORY_CRAFTS("category:crafts"),

    GA_SPAM("special:gaspam"),

    PDF("format:pdf"),

    POPOVER("special:popover"),
    CONSENT("special:consent"),
    SHORT_DOCUMENT("special:shorty"),
    THIRD_PARTY_REQUESTS("special:3pr"),

    // Here!  It is generally safe to add additional values here without
    // disrupting the encoded values used by the DocumentValuator
    // class in the index!

    /** For fingerprinting and ranking */
    OPENGRAPH("special:opengraph"),
    OPENGRAPH_IMAGE("special:opengraph:image"),
    TWITTERCARD("special:twittercard"),
    TWITTERCARD_IMAGE("special:twittercard:image"),
    FONTAWSESOME("special:fontawesome"),
    GOOGLEFONTS("special:googlefonts"),
    DNS_PREFETCH("special:dnsprefetch"),
    PRELOAD("special:preload"),
    PRECONNECT("special:preconnect"),
    PINGBACK("special:pingback"),
    FEED("special:feed"),
    WEBMENTION("special:webmention"),
    INDIEAUTH("special:indieauth"),
    ME_TAG("special:metag"),
    NEXT_TAG("special:nexttag"),
    AMPHTML("special:amphtml"),
    JSON_LD("special:jsonld"),
    ORIGIN_TRIAL("special:origintrial"),
    PROFILE_GMPG("special:profile-gpmg"),
    QUANTCAST("special:quantcast"),
    COOKIELAW("special:cookielaw"),
    DIDOMI("special:didomi"),
    PARDOT("special:pardot"),
    ONESIGNAL("special:onesignal"),
    DATE_TAG("special:date_tag"),
    NOSCRIPT_TAG("special:noscript_tag"),

    ROBOTS_INDEX("robots:index"),
    ROBOTS_FOLLOW("robots:follow"),
    ROBOTS_NOODP("robots:noodp"),
    ROBOTS_NOYDIR("robots:noydir"),
    DOFOLLOW_LINK("special:dofollow"),
    APPLE_TOUCH_ICON("special:appleicon"),

    S3_FEATURE("special:s3"),

    MISSING_DOM_SAMPLE("special:nosample"),
    UNKNOWN("special:uncategorized");


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

    public int getFeatureBit() {
        if (getClass().desiredAssertionStatus() && ordinal() >= 32)
            throw new IllegalStateException("Attempting to extract feature bit of " + name() + ", with ordinal " + ordinal());
        return (1<< ordinal());
    }
}
