package nu.marginalia.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.adblock.AdblockSimulator;
import nu.marginalia.adblock.GoogleAnwersSpamDetector;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.topic.RecipeDetector;
import nu.marginalia.topic.TextileCraftDetector;
import nu.marginalia.topic.WoodworkingDetector;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class FeatureExtractor {

    private static final List<String> innocentTrackers = List.of(
            "twitter.com",
            "bing.com",
            "msn.com",
            "mail.ru/counter"
            );
    private static final List<String> adtechTrackers = List.of(
            "publir.com",
            "adform.net",
            "connect.facebook",
            "facebook.com/tr",
            "absbygoogle.com",
            "adnxs.com",
            "monsterinsights",
            "googletagmanager.com",
            "googlesyndication.com",
            "smartadserver.com",
            "doubleclick.com",
            "2mdn.com",
            "dmtry.com",
            "amazon-adsystem.com",
            "alexametrics.com",
            "rubiconproject.com",
            "chango.com",
            "d5nxst8fruw4z.cloudfront.net",
            "d31qbv1cthcecs.cloudfront.net",
            "linkedin.com",
            "perfectaudience.com",
            "marketingautomation.services",
            "usefathom",
            "adthrive",
            "wordads",
            "wa_smart",
            "personalized-ads-consent",
            "_taboola",
            "nativeads",
            "skimlinks"
    );

    private final AdblockSimulator adblockSimulator;
    private final RecipeDetector recipeDetector;
    private final TextileCraftDetector textileCraftDetector;
    private final WoodworkingDetector woodworkingDetector;
    private final GoogleAnwersSpamDetector googleAnwersSpamDetector;

    @Inject
    public FeatureExtractor(AdblockSimulator adblockSimulator,
                            RecipeDetector recipeDetector,
                            TextileCraftDetector textileCraftDetector,
                            WoodworkingDetector woodworkingDetector,
                            GoogleAnwersSpamDetector googleAnwersSpamDetector)
    {
        this.adblockSimulator = adblockSimulator;
        this.recipeDetector = recipeDetector;
        this.textileCraftDetector = textileCraftDetector;
        this.woodworkingDetector = woodworkingDetector;
        this.googleAnwersSpamDetector = googleAnwersSpamDetector;
    }

    public Set<HtmlFeature> getFeatures(EdgeUrl url, Document doc, DocumentLanguageData dld) {
        final Set<HtmlFeature> features = new HashSet<>();

        final Elements scriptTags = doc.getElementsByTag("script");

        if (googleAnwersSpamDetector.testP(doc) > 0.5) {
            features.add(HtmlFeature.GA_SPAM);
        }

        if (isKebabCase(url)) {
            features.add(HtmlFeature.KEBAB_CASE_URL);
        }
        if (url.path.length() > 64) {
            features.add(HtmlFeature.LONG_URL);
        }

        for (var scriptTag : scriptTags) {
            final String type = scriptTag.attr("type");

            if ("application/ld+json".equalsIgnoreCase(type)) {
                features.add(HtmlFeature.JSON_LD);
            }
            else {
                features.add(HtmlFeature.JS);
            }
        }

        if (!doc.head().getElementsByTag("viewport").isEmpty()) {
            features.add(HtmlFeature.VIEWPORT);
        }
        for (var atag : doc.body().getElementsByTag("a")) {
            var rel = atag.attr("rel");
            if (rel.equals("dofollow")) {
                features.add(HtmlFeature.DOFOLLOW_LINK);
            }
        }

        if (!doc.getElementsByTag("date").isEmpty()) {
            features.add(HtmlFeature.DATE_TAG);
        }
        if (!doc.getElementsByTag("noscript").isEmpty()) {
            features.add(HtmlFeature.NOSCRIPT_TAG);
        }


        for (var link : doc.head().getElementsByTag("link")) {

            // 500 IQ web developers use <link> error or load handlers
            // sneakily load JS without explicit script tags
            if (link.hasAttr("onerror"))
                features.add(HtmlFeature.JS);
            if (link.hasAttr("onload"))
                features.add(HtmlFeature.JS);

            if (link.hasAttr("pingback")) {
                features.add(HtmlFeature.PINGBACK);
            }


            var href = link.attr("href");

            if (href.contains("indieauth"))
                features.add(HtmlFeature.INDIEAUTH);

            var rel = link.attr("rel");

            if (rel.equals("webmention"))
                features.add(HtmlFeature.WEBMENTION);

            if (rel.equals("me"))
                features.add(HtmlFeature.ME_TAG);

            if (rel.equals("next"))
                features.add(HtmlFeature.NEXT_TAG);

            if (rel.equals("alternate") && link.hasAttr("type"))
                features.add(HtmlFeature.FEED);

            if (rel.equals("dns-prefetch"))
                features.add(HtmlFeature.DNS_PREFETCH);

            if (rel.equals("preload"))
                features.add(HtmlFeature.PRELOAD);

            if (rel.equals("preconnect"))
                features.add(HtmlFeature.PRECONNECT);

            if (rel.equals("amphtml"))
                features.add(HtmlFeature.AMPHTML);

            if (rel.equals("apple-touch-icon"))
                features.add(HtmlFeature.APPLE_TOUCH_ICON);

        }

        for (var meta : doc.head().getElementsByTag("meta")) {
            // <meta name="robots" content="index,follow">
            if (meta.attr("name").equals("robots")) {
                var content = meta.attr("content");
                if (!content.contains("noindex") && content.contains("index")) {
                    features.add(HtmlFeature.ROBOTS_INDEX);
                }
                if (!content.contains("nofollow") && content.contains("follow")) {
                    features.add(HtmlFeature.ROBOTS_FOLLOW);
                }
                if (content.contains("noodp")) {
                    features.add(HtmlFeature.ROBOTS_NOODP);
                }
                if (content.contains("noydir")) {
                    features.add(HtmlFeature.ROBOTS_NOYDIR);
                }
            }

            if (meta.attr("profile").contains("gmpg")) {
                features.add(HtmlFeature.PROFILE_GMPG);
            }
            if (meta.attr("property").equals("og:description")) {
                features.add(HtmlFeature.OPENGRAPH);
            }
            if (meta.attr("property").equals("og:image")) {
                features.add(HtmlFeature.OPENGRAPH_IMAGE);
            }
            if (meta.attr("name").equals("twitter:description")) {
                features.add(HtmlFeature.TWITTERCARD);
            }
            if (meta.attr("name").equals("twitter:image")) {
                features.add(HtmlFeature.TWITTERCARD_IMAGE);
            }
            if (meta.attr("http-equiv").equals("origin-trial")) {
                features.add(HtmlFeature.ORIGIN_TRIAL);
            }
        }

        if (features.contains(HtmlFeature.JS) && adblockSimulator.hasAds(doc.clone())) {
            features.add(HtmlFeature.ADVERTISEMENT);
        }

        if (!doc.getElementsByTag("object").isEmpty()
                || !doc.getElementsByTag("audio").isEmpty()
                || !doc.getElementsByTag("video").isEmpty()) {
            features.add(HtmlFeature.MEDIA);
        }

        for (var scriptTag : scriptTags) {
            if (hasInvasiveTrackingScript(scriptTag)) {
                features.add(HtmlFeature.TRACKING);
                features.add(HtmlFeature.TRACKING_ADTECH);
            }
            else if (hasNaiveTrackingScript(scriptTag)) {
                features.add(HtmlFeature.TRACKING);
            }

            if (scriptTag.hasAttr("didomi/javascript")) {
                features.add(HtmlFeature.DIDOMI);
            }

            String src = scriptTag.attr("src");
            if (src.contains("OneSignalSDK")) {
                features.add(HtmlFeature.ONESIGNAL);
            }

            String scriptText = scriptTag.html();

            if (scriptText.contains("_ga=") || scriptText.contains("ga('create'")) {
                features.add(HtmlFeature.TRACKING);
            }
            if (scriptText.contains("_tmr")) {
                features.add(HtmlFeature.TRACKING);
            }
            if (scriptText.contains("'pd.js'")) {
                features.add(HtmlFeature.PARDOT);
            }
            if (scriptText.contains("https://cmp.quantcast.com")) {
                features.add(HtmlFeature.QUANTCAST);
            }
            if (scriptText.contains("https://quantcast.mgr.consensu.org")) {
                features.add(HtmlFeature.QUANTCAST);
            }
            if (scriptText.contains("https://cdn.cookielaw.org")) {
                features.add(HtmlFeature.COOKIELAW);
            }
            if (scriptText.contains("_linkedin_data_partner_id")) {
                features.add(HtmlFeature.TRACKING);
                features.add(HtmlFeature.TRACKING_ADTECH);
            }
            if (scriptText.contains("window.OneSignal")) {
                features.add(HtmlFeature.ONESIGNAL);
            }
            if (scriptText.contains("connect.facebook.net")) {
                features.add(HtmlFeature.TRACKING);
                features.add(HtmlFeature.TRACKING_ADTECH);
            }
            if (scriptText.contains("hotjar.com")) {
                features.add(HtmlFeature.TRACKING);
            }
        }

        for (var noscript : doc.getElementsByTag("noscript")) {
            for (var iframe : noscript.getElementsByTag("iframe")) {
                if (hasInvasiveTrackingScript(iframe)) {
                    features.add(HtmlFeature.TRACKING);
                    features.add(HtmlFeature.TRACKING_ADTECH);
                }
                else if (hasNaiveTrackingScript(iframe)) {
                    features.add(HtmlFeature.TRACKING);
                }
            }
            for (var img : noscript.getElementsByTag("img")) {
                if (hasInvasiveTrackingScript(img)) {
                    features.add(HtmlFeature.TRACKING);
                    features.add(HtmlFeature.TRACKING_ADTECH);
                }
                else if (hasNaiveTrackingScript(img)) {
                    features.add(HtmlFeature.TRACKING);
                }
            }
        }

        if (scriptTags.html().contains("google-analytics.com")) {
            features.add(HtmlFeature.TRACKING);
        }

        for (var aTag : doc.getElementsByTag("a")) {
            if (isAmazonAffiliateLink(aTag)) {
                features.add(HtmlFeature.AFFILIATE_LINK);
                break;
            }
        }

        if (recipeDetector.testP(dld) > 0.5)
            features.add(HtmlFeature.CATEGORY_FOOD);
        // these should be mutually exclusive
        else if (woodworkingDetector.testP(dld) > 0.3 || textileCraftDetector.testP(dld) > 0.3)
            features.add(HtmlFeature.CATEGORY_CRAFTS);

        return features;
    }

    private boolean isKebabCase(EdgeUrl url) {
        return url.path.chars().filter(c -> c=='-').count() > 3;
    }

    private boolean hasInvasiveTrackingScript(Element scriptTag) {
        return hasInvasiveTrackingScript(scriptTag.attr("src"));
    }
    private boolean hasNaiveTrackingScript(Element scriptTag) {
        return hasNaiveTrackingScript(scriptTag.attr("src"));
    }
    private boolean hasInvasiveTrackingScript(String src) {

        for (var tracker : adtechTrackers) {
            if (src.contains(tracker)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNaiveTrackingScript(String src) {

        for (var tracker : innocentTrackers) {
            if (src.contains(tracker)) {
                return true;
            }
        }
        return false;
    }


    boolean isAmazonAffiliateLink(Element aTag) {
        final String href = aTag.attr("href").toLowerCase();

        if (href.contains("amzn.to/"))
            return true;
        if (href.contains("amazon.com/") && href.contains("tag="))
            return true;

        return false;
    }
}
