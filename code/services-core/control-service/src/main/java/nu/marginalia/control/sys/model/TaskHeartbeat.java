package nu.marginalia.control.sys.model;


public record TaskHeartbeat(
        String taskName,
        String taskBase,
        int node,
        String instanceUuidFull,
        String serviceUuuidFull,
        double lastSeenMillis,
        Integer progress,
        String stage,
        String status
) {
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

}
