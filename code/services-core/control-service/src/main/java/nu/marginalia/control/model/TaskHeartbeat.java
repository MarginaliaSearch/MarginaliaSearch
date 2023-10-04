package nu.marginalia.control.model;


public record TaskHeartbeat(
        String taskName,
        String taskBase,
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
