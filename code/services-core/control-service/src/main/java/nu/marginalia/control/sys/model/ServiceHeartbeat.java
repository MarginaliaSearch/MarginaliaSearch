package nu.marginalia.control.sys.model;

public record ServiceHeartbeat(
        String serviceId,
        String serviceBase,
        String uuidFull,
        double lastSeenMillis,
        boolean alive
) {
    public boolean isMissing() {
        return lastSeenMillis > 10000;
    }
}
