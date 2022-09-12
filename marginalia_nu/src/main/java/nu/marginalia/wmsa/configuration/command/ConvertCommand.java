package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.converting.ConverterMain;

import java.util.Arrays;

public class ConvertCommand extends Command {
    public ConvertCommand() {
        super("convert");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        if (args.length < 2) {
            System.err.println("Usage: convert plan.yaml");
            System.exit(255);
        }

        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        ConverterMain.main(args2);
    }
}
