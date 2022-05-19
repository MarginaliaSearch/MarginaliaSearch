package nu.marginalia.wmsa.configuration.command;

import nu.marginalia.wmsa.configuration.ServiceDescriptor;

import java.util.Arrays;
import java.util.Objects;

public abstract class Command {
    public final String name;

    protected Command(String name) {
        this.name = name;
    }

    public abstract void execute(String... args);

    static ServiceDescriptor getKind(String arg) {

        try {
            return Arrays.stream(ServiceDescriptor.values())
                    .filter(sd -> Objects.equals(arg, sd.name))
                    .findFirst()
                    .orElseThrow(IllegalArgumentException::new)
                    ;
        } catch (IllegalArgumentException ex) {
            System.err.println("Unknown service '" + arg + "'");
            System.exit(1);
        }
        return null;
    }
}
