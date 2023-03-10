package nu.marginalia.search.model;

import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.index.client.model.query.EdgeSearchSubquery;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;

import java.util.Objects;

public enum SearchProfile {

    DEFAULT("default",  SearchSetIdentifier.RETRO),
    MODERN("modern", SearchSetIdentifier.SMALLWEB),
    CORPO("corpo", SearchSetIdentifier.NONE),
    YOLO("yolo", SearchSetIdentifier.NONE),
    VINTAGE("vintage", SearchSetIdentifier.NONE),
    CORPO_CLEAN("corpo-clean",  SearchSetIdentifier.NONE),
    ACADEMIA("academia",  SearchSetIdentifier.ACADEMIA),

    PLAIN_TEXT("plain-text", SearchSetIdentifier.NONE),
    FOOD("food", SearchSetIdentifier.NONE),
    CRAFTS("crafts", SearchSetIdentifier.NONE),
    CLASSICS("classics", SearchSetIdentifier.NONE),
    ;


    public final String name;
    public final SearchSetIdentifier searchSetIdentifier;

    SearchProfile(String name, SearchSetIdentifier searchSetIdentifier) {
        this.name = name;
        this.searchSetIdentifier = searchSetIdentifier;
    }

    private final static SearchProfile[] values = values();
    public static SearchProfile getSearchProfile(String param) {
        if (null == param) {
            return YOLO;
        }

        for (var profile : values) {
            if (Objects.equals(profile.name, param)) {
                return profile;
            }
        }

        return YOLO;
    }

    public void addTacitTerms(EdgeSearchSubquery subquery) {
        if (this == ACADEMIA) {
            subquery.searchTermsPriority.add("tld:edu");
        }
        if (this == VINTAGE) {
            subquery.searchTermsPriority.add("format:html123");
            subquery.searchTermsPriority.add("js:false");
        }
        if (this == PLAIN_TEXT) {
            subquery.searchTermsInclude.add("format:plain");
        }
        if (this == FOOD) {
            subquery.searchTermsInclude.add(HtmlFeature.CATEGORY_FOOD.getKeyword());
        }
        if (this == CRAFTS) {
            subquery.searchTermsInclude.add(HtmlFeature.CATEGORY_CRAFTS.getKeyword());
        }
    }

    public SpecificationLimit getYearLimit() {
        if (this == MODERN) {
            return SpecificationLimit.greaterThan(2015);
        }
        if (this == VINTAGE) {
            return SpecificationLimit.lessThan(2003);
        }
        else return SpecificationLimit.none();
    }

    public SpecificationLimit getSizeLimit() {
        if (this == MODERN) {
            return SpecificationLimit.lessThan(500);
        }
        else return SpecificationLimit.none();
    }


    public SpecificationLimit getQualityLimit() {
        if (this == MODERN) {
            return SpecificationLimit.lessThan(5);
        }
        else return SpecificationLimit.none();
    }



    public String getNearDomain() {
        if (this == CLASSICS) {
            return "classics.mit.edu";
        }
        return null;
    }
}

