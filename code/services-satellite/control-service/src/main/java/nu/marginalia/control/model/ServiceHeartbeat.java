package nu.marginalia.control.model;

public record ServiceHeartbeat(
        String serviceId,
        String serviceBase,
        String uuid,
        double lastSeenMillis,
        boolean alive
) {
    public boolean isMissing() {
        return lastSeenMillis > 10000;
    }

}
