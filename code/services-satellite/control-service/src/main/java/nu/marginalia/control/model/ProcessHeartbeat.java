package nu.marginalia.control.model;

import nu.marginalia.control.svc.ProcessService;

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
    public boolean isRunning() {
        return "RUNNING".equals(status);
    }
    public String progressStyle() {
        if ("RUNNING".equals(status) && progress != null) {
            return """
                    background: linear-gradient(90deg, #fff 0%%, #ccc %d%%, #fff %d%%)
                    """.formatted(progress, progress, progress);
        }
        return "";
    }

    public ProcessService.ProcessId getProcessId() {
        return switch (processBase) {
            case "converter" -> ProcessService.ProcessId.CONVERTER;
            case "crawler" -> ProcessService.ProcessId.CRAWLER;
            case "loader" -> ProcessService.ProcessId.LOADER;
            default -> throw new RuntimeException("Unknown process base: " + processBase);
        };
    }
}
