package nu.marginalia.control.app.model;

public enum DomainComplaintCategory {
    SPAM("spam"),
    FREEBOOTING("freebooting"),
    BROKEN("broken"),
    SHOCK("shock"),
    BLACKLIST("blacklist"),
    REMOVE_EXPLORATION("no-random"),
    UNKNOWN("unknown");

    private final String categoryName;

    DomainComplaintCategory(String categoryName) {
        this.categoryName = categoryName;
    }

    public String categoryName() {
        return categoryName;
    }
    public static DomainComplaintCategory fromCategoryName(String categoryName) {
        for (DomainComplaintCategory category : values()) {
            if (category.categoryName().equals(categoryName)) {
                return category;
            }
        }
        return UNKNOWN;
    }
}
