package nu.marginalia.control.model;

public record ProcessHeartbeat(
        String processId,
        String processBase,
        String uuidFull,
        double lastSeenMillis,
        Integer progress,
        String status
) {
    public String uuid() {
        return uuidFull.substring(0, 8);
    }
    public String uuidColor() {
        return '#' + uuidFull.substring(0, 6);
    }
    public String uuidColor2() {
        return '#' + uuidFull.substring(25, 31);
    }
    public boolean isMissing() {
        return lastSeenMillis > 10000;
    }
    public boolean isStopped() {
        return "STOPPED".equals(status);
    }
    public String progressStyle() {
        if ("RUNNING".equals(status) && progress != null) {
            return """
                    background: linear-gradient(90deg, #fff 0%%, #ccc %d%%, #fff %d%%)
                    """.formatted(progress, progress, progress);
        }
        return "";
    }
}
