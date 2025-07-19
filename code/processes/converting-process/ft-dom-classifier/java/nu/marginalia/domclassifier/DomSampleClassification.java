package nu.marginalia.domclassifier;

import nu.marginalia.model.crawl.HtmlFeature;

import javax.annotation.Nullable;

/**
 * Feature classifications for the DOM sample
 */
public enum DomSampleClassification {
    ADS(HtmlFeature.ADVERTISEMENT),
    TRACKING(HtmlFeature.TRACKING_ADTECH),
    CONSENT(HtmlFeature.CONSENT),
    POPOVER(HtmlFeature.POPOVER),
    UNCLASSIFIED(HtmlFeature.MISSING_DOM_SAMPLE),
    IGNORE(null);

    @Nullable
    public final HtmlFeature htmlFeature;

    DomSampleClassification(@Nullable HtmlFeature feature) {
        this.htmlFeature = feature;
    }
}
