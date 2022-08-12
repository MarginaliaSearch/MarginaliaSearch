package nu.marginalia.wmsa.edge.converting.processor.logic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.converting.processor.logic.topic.AdblockSimulator;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import org.jsoup.nodes.Document;

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
        Set<HtmlFeature> features = new HashSet<>();

        var scriptTags = doc.getElementsByTag("script");

        if (scriptTags.size() > 0) {
            features.add(HtmlFeature.JS);
        }
        else if(adblockSimulator.hasAds(doc.clone())) { // Only look for ads if there is javascript
            features.add(HtmlFeature.ADVERTISEMENT);
        }

        if (!doc.getElementsByTag("object").isEmpty()
                || !doc.getElementsByTag("audio").isEmpty()
                || !doc.getElementsByTag("video").isEmpty()) {
            features.add(HtmlFeature.MEDIA);
        }

        if (scriptTags.stream()
                .anyMatch(tag -> trackers.stream().anyMatch(tracker -> tag.attr("src").contains(tracker)))) {
            features.add(HtmlFeature.TRACKING);
        }

        if (scriptTags.html().contains("google-analytics.com")) {
            features.add(HtmlFeature.TRACKING);
        }

        if (doc.getElementsByTag("a").stream().map(e -> e.attr("href"))
                .map(String::toLowerCase)
                .anyMatch(href ->
                        href.contains("amzn.to/") || (href.contains("amazon.com/") & href.contains("tag=")))) {
            features.add(HtmlFeature.AFFILIATE_LINK);
        }

        if (!domain.cookies.isEmpty()) {
            features.add(HtmlFeature.COOKIES);
        }

        return features;
    }
}
