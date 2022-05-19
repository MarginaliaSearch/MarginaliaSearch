package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;

import java.util.Arrays;
import java.util.Objects;

public class ListCommand extends Command {
    public ListCommand() {
        super("list");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        Arrays.stream(ServiceDescriptor.values())
                .filter(sd -> Objects.nonNull(sd.mainClass))
                .map(ServiceDescriptor::describeService)
                .forEach(System.out::println);
    }

}
