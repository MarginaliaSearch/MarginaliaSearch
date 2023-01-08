package nu.marginalia.wmsa.edge.search.model;

import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimit;

import java.util.Objects;

public enum EdgeSearchProfile {

    DEFAULT("default",  SearchSetIdentifier.RETRO),
    MODERN("modern", SearchSetIdentifier.SMALLWEB),
    CORPO("corpo", SearchSetIdentifier.NONE),
    YOLO("yolo", SearchSetIdentifier.NONE),
    CORPO_CLEAN("corpo-clean",  SearchSetIdentifier.NONE),
    ACADEMIA("academia",  SearchSetIdentifier.ACADEMIA),

    FOOD("food", SearchSetIdentifier.NONE),
    CRAFTS("crafts", SearchSetIdentifier.NONE),

    CLASSICS("classics", SearchSetIdentifier.NONE),
    ;


    public final String name;
    public final SearchSetIdentifier searchSetIdentifier;

    EdgeSearchProfile(String name, SearchSetIdentifier searchSetIdentifier) {
        this.name = name;
        this.searchSetIdentifier = searchSetIdentifier;
    }

    private final static EdgeSearchProfile[] values = values();
    public static EdgeSearchProfile getSearchProfile(String param) {
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

