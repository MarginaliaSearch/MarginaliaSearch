package nu.marginalia.control.model;

public record ServiceHeartbeat(
        String serviceId,
        String serviceBase,
        String uuid,
        double lastSeenMillis,
        boolean alive
) {

}
