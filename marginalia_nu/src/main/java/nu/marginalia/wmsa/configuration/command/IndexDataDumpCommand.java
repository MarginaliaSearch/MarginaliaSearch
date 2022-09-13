package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.tools.IndexJournalDumpTool;

import java.util.Arrays;

public class IndexDataDumpCommand extends Command {
    public IndexDataDumpCommand() {
        super("index-dump");
    }

    @SneakyThrows
    @Override
    public void execute(String... args) {
        if (args.length < 1) {
            System.err.println("Usage: index-dump [sub-command] index.dat");
            System.exit(255);
        }

        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        IndexJournalDumpTool.main(args2);
    }
}
