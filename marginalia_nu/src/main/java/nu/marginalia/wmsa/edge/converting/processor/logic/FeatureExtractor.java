package nu.marginalia.wmsa.edge.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.converting.processor.logic.topic.AdblockSimulator;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
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

    private AdblockSimulator adblockSimulator;

    @Inject
    public FeatureExtractor(AdblockSimulator adblockSimulator) {
        this.adblockSimulator = adblockSimulator;
    }

    public Set<HtmlFeature> getFeatures(CrawledDomain domain, Document doc) {
        final Set<HtmlFeature> features = new HashSet<>();

        final Elements scriptTags = doc.getElementsByTag("script");

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

        if (!domain.cookies.isEmpty()) {
            features.add(HtmlFeature.COOKIES);
        }

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
