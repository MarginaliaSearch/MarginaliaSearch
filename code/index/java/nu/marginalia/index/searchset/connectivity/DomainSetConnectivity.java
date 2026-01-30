package nu.marginalia.index.searchset.connectivity;

public enum DomainSetConnectivity {
    UNKNOWN,
    DIRECT,
    BIDI_HOT,
    BIDI,
    REACHABLE_HOT,
    REACHABLE,
    LINKING_HOT,
    LINKING,
    UNREACHABLE;

    public boolean isPeripheral() {
        return switch (this) {
            case REACHABLE, LINKING, UNREACHABLE -> true;
            default -> false;
        };
    }
}
