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
                    background: linear-gradient(90deg, #ccc 0%%, #ccc %d%%, #fff %d%%)
                    """.formatted(progress, progress, progress);
        }
        return "";
    }

    public ProcessService.ProcessId getProcessId() {
        return switch (processBase) {
            case "converter" -> ProcessService.ProcessId.CONVERTER;
            case "crawler" -> ProcessService.ProcessId.CRAWLER;
            case "loader" -> ProcessService.ProcessId.LOADER;
            case "website-adjacencies-calculator" -> ProcessService.ProcessId.ADJACENCIES_CALCULATOR;
            case "crawl-job-extractor" -> ProcessService.ProcessId.CRAWL_JOB_EXTRACTOR;
            default -> null;
        };
    }

    public String displayName() {
        var pid = getProcessId();
        if (pid != null) {
            return pid.name();
        }
        else {
            return processBase;
        }
    }
}
