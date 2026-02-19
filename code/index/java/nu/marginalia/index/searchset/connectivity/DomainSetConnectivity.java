package nu.marginalia.index.searchset.connectivity;

public enum DomainSetConnectivity {
    // Don't re-order these, they are saved on disk by ordinal
    UNKNOWN(255),
    DIRECT(0),
    BIDI_HOT(16),
    BIDI(64),
    REACHABLE_HOT(32),
    REACHABLE(80),
    LINKING_HOT(48),
    LINKING(96),
    UNREACHABLE(255);

    public final int rankValue;

    DomainSetConnectivity(int rankValue) {
        this.rankValue = rankValue;
    }

    public boolean isPeripheral() {
        return switch (this) {
            case REACHABLE, LINKING, UNREACHABLE -> true;
            default -> false;
        };
    }
}
