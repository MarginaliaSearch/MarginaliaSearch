package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;

import java.util.Arrays;

public class StartCommand extends Command {
    public StartCommand() {
        super("start");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        if (args.length < 2) {
            System.err.println("Usage: start service-descriptor");
            System.exit(255);
        }

        var mainMethod = getKind(args[1]).mainClass.getMethod("main", String[].class);
        String[] args2 = Arrays.copyOfRange(args, 2, args.length);
        mainMethod.invoke(null, (Object) args2);
    }
}
