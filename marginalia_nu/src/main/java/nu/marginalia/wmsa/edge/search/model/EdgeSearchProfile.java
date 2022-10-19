package nu.marginalia.wmsa.edge.search.model;

import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public enum EdgeSearchProfile {

    DEFAULT("default", SearchOrder.DEFAULT_ORDER, 0, 1),
    MODERN("modern", SearchOrder.DEFAULT_ORDER, 2),
    CORPO("corpo", SearchOrder.DEFAULT_ORDER, 4, 5, 7),
    YOLO("yolo", SearchOrder.DEFAULT_ORDER, 0, 2, 1, 3, 6),
    CORPO_CLEAN("corpo-clean", SearchOrder.DEFAULT_ORDER, 0, 1),
    ACADEMIA("academia",  SearchOrder.DEFAULT_ORDER, 3),

    FOOD("food", SearchOrder.DEFAULT_ORDER, 2, 0),
    CRAFTS("crafts", SearchOrder.DEFAULT_ORDER, 2, 0),

    CLASSICS("classics", SearchOrder.DEFAULT_ORDER, 4, 5, 7),
    ;


    public final String name;
    public final List<Integer> buckets;
    public final List<IndexBlock> indexBlocks;

    EdgeSearchProfile(String name,
                      List<IndexBlock> indexBlocks,
                      int... buckets) {
        this.name = name;
        this.indexBlocks = indexBlocks;
        this.buckets = Arrays.stream(buckets).boxed().collect(Collectors.toList());
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
        if (this == FOOD) {
            subquery.searchTermsInclude.add(HtmlFeature.CATEGORY_FOOD.getKeyword());
        }
        if (this == CRAFTS) {
            subquery.searchTermsInclude.add(HtmlFeature.CATEGORY_CRAFTS.getKeyword());
        }
    }

    public String getNearDomain() {
        if (this == CLASSICS) {
            return "classics.mit.edu";
        }
        return null;
    }
}

