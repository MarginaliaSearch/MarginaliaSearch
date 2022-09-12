package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.converting.LoaderMain;

import java.util.Arrays;

public class LoadCommand extends Command {
    public LoadCommand() {
        super("load");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        if (args.length < 2) {
            System.err.println("Usage: load plan.yaml");
            System.exit(255);
        }
        
        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        LoaderMain.main(args2);
    }
}
