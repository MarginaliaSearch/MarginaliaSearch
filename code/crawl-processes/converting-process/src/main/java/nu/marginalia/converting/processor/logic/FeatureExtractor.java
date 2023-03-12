package nu.marginalia.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.adblock.AdblockSimulator;
import nu.marginalia.adblock.GoogleAnwersSpamDetector;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.language.model.DocumentLanguageData;
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

    private static final List<String> trackers = List.of("adform.net",
            "connect.facebook",
            "googletagmanager.com",
            "googlesyndication.com",
            "google.com",
            "twitter.com",
            "smartadserver.com",
            "doubleclick.com",
            "2mdn.com",
            "dmtry.com",
            "bing.com",
            "msn.com",
            "amazon-adsystem.com",
            "alexametrics.com",
            "rubiconproject.com",
            "chango.com",
            "d5nxst8fruw4z.cloudfront.net",
            "d31qbv1cthcecs.cloudfront.net",
            "linkedin.com");

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

    public Set<HtmlFeature> getFeatures(CrawledDomain domain, Document doc, DocumentLanguageData dld) {
        final Set<HtmlFeature> features = new HashSet<>();

        final Elements scriptTags = doc.getElementsByTag("script");

        if (googleAnwersSpamDetector.testP(doc) > 0.5) {
            features.add(HtmlFeature.GA_SPAM);
        }

        for (var scriptTag : scriptTags) {
            if (isJavascriptTag(scriptTag)) {
                features.add(HtmlFeature.JS);
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
            if (hasTrackingScript(scriptTag)) {
                features.add(HtmlFeature.TRACKING);
                break;
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

        if (!domain.cookies.isEmpty())
            features.add(HtmlFeature.COOKIES);

        if (recipeDetector.testP(dld) > 0.5)
            features.add(HtmlFeature.CATEGORY_FOOD);
        // these should be mutually exclusive
        else if (woodworkingDetector.testP(dld) > 0.3 || textileCraftDetector.testP(dld) > 0.3)
            features.add(HtmlFeature.CATEGORY_CRAFTS);

        return features;
    }

    private boolean hasTrackingScript(Element scriptTag) {
        for (var tracker : trackers) {
            if (scriptTag.attr("src").contains(tracker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJavascriptTag(Element scriptTag) {
        final String type = scriptTag.attr("type");

        if ("application/ld+json".equalsIgnoreCase(type)) {
            return false;
        }

        return true;
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
