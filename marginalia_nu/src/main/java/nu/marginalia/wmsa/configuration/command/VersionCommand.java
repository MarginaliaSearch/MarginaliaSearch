package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;

public class VersionCommand extends Command {
    public VersionCommand() {
        super("version");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        try (var str = ClassLoader.getSystemResourceAsStream("_version.txt")) {
            if (null == str) {
                System.err.println("Bad jar, missing _version.txt");
                return;
            }
        }
    }
}
