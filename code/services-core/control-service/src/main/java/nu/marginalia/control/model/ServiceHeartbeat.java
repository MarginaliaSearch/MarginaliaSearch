package nu.marginalia.control.model;

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
    public String uuid() {
        return uuidFull.substring(0, 8);
    }
    public String uuidColor() {
        return '#' + uuidFull.substring(0, 6);
    }
    public String uuidColor2() {
        return '#' + uuidFull.substring(25, 31);
    }
}
