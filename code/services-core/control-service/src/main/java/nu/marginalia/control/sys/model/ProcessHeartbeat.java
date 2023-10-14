package nu.marginalia.control.sys.model;

public record ProcessHeartbeat(
        String processId,
        String processBase,
        int node,
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
    public boolean isRunning() {
        return "RUNNING".equals(status);
    }
    public String progressStyle() {
        if ("RUNNING".equals(status) && progress != null) {
            return """
                    background: linear-gradient(90deg, #ccc 0%%, #ccc %d%%, #fff %d%%)
                    """.formatted(progress, progress, progress);
        }
        return "";
    }

    public String displayName() {
        return processId;
    }

}
