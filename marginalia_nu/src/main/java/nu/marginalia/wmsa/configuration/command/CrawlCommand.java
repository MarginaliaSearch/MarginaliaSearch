package nu.marginalia.wmsa.configuration.command;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawling.CrawlerMain;

import java.util.Arrays;

public class CrawlCommand extends Command {
    public CrawlCommand() {
        super("crawl");
    }

    @Override
    @SneakyThrows
    public void execute(String... args) {
        if (args.length < 2) {
            System.err.println("Usage: crawl plan.yaml");
            System.exit(255);
        }

        String[] args2 = Arrays.copyOfRange(args, 1, args.length);
        CrawlerMain.main(args2);
    }
}
