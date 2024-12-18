package nu.marginalia.api.domains.model;

import nu.marginalia.model.EdgeUrl;

public record SimilarDomain(EdgeUrl url,
                            int domainId,
                            double relatedness,
                            double rank,
                            boolean indexed,
                            boolean active,
                            boolean screenshot,
                            LinkType linkType) {

    public String getRankSymbols() {
        if (rank > 90) {
            return "&#9733;&#9733;&#9733;&#9733;&#9733;";
        }
        if (rank > 70) {
            return "&#9733;&#9733;&#9733;&#9733;";
        }
        if (rank > 50) {
            return "&#9733;&#9733;&#9733;";
        }
        if (rank > 30) {
            return "&#9733;&#9733;";
        }
        if (rank > 10) {
            return "&#9733;";
        }
        return "";
    }

    public enum LinkType {
        BACKWARD,
        FOWARD,
        BIDIRECTIONAL,
        NONE;

        public boolean isLinked() {
            return this != NONE;
        }

        public static LinkType find(boolean linkStod,
                                    boolean linkDtos) {
            if (linkDtos && linkStod)
                return BIDIRECTIONAL;
            if (linkDtos)
                return FOWARD;
            if (linkStod)
                return BACKWARD;

            return NONE;
        }

        public String faIcon() {
            return switch (this) {
                case FOWARD -> "fa-solid fa-arrow-right";
                case BACKWARD -> "fa-solid fa-arrow-left";
                case BIDIRECTIONAL -> "fa-solid fa-arrow-right-arrow-left";
                case NONE -> "";
            };
        }

        public String getDescription() {
            return switch (this) {
                case BACKWARD -> "Backward Link";
                case FOWARD -> "Forward Link";
                case BIDIRECTIONAL -> "Mutual Link";
                case NONE -> "No Link";
            };
        }
    }
}
