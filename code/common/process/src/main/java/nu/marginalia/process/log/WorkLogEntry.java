package nu.marginalia.process.log;

import org.apache.logging.log4j.util.Strings;

public record WorkLogEntry(String id, String ts, String path, int cnt) {

    static WorkLogEntry parse(String line) {
        String[] parts = line.split("\\s+");
        return new WorkLogEntry(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    static boolean isJobId(String line) {
        return Strings.isNotBlank(line) && !line.startsWith("#");
    }
}
