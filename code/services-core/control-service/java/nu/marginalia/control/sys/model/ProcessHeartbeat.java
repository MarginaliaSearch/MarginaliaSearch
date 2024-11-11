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
            return "background: linear-gradient(90deg, #ccc 0%, #ccc " + progress + "%, #fff " + progress + "%)";
        }
        return "";
    }

    public String displayName() {
        return processId;
    }

}
