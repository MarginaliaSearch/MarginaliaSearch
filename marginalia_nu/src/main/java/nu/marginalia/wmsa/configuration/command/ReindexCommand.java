package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.converting.ReindexTriggerMain;

import java.util.Arrays;

public class ReindexCommand extends Command {
    public ReindexCommand() {
        super("reindex");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        if (args.length < 2) {
            System.err.println("Usage: reindex host");
            System.exit(255);
        }
        
        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        ReindexTriggerMain.main(args2);
    }
}
