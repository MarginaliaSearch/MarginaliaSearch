package nu.marginalia.api.searchquery.model.query;

public enum NsfwFilterTier {
    OFF(0),
    DANGER(1),
    PORN_AND_GAMBLING(2);

    private final int codedValue; // same as ordinal() for now, but can be changed later if needed

    NsfwFilterTier(int codedValue) {
        this.codedValue = codedValue;
    }

    public static NsfwFilterTier fromCodedValue(int codedValue) {
        for (NsfwFilterTier tier : NsfwFilterTier.values()) {
            if (tier.codedValue == codedValue) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Invalid coded value for NsfwFilterTirer: " + codedValue);
    }

    public int getCodedValue() {
        return codedValue;
    }
}
