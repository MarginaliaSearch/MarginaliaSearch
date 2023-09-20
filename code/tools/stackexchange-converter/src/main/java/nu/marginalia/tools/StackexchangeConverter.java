package nu.marginalia.tools;

import nu.marginalia.integration.stackexchange.sqlite.StackExchangePostsDb;

import java.nio.file.Files;
import java.nio.file.Path;

public class StackexchangeConverter {
    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Converts a stackexchange Posts 7z file to a Marginalia-digestible sqlite-db\n");
            System.err.println("Arguments: domain-name input-file.7z output-file.db");
            return;
        }

        String domain = args[0];

        Path inputFile = Path.of(args[1]);
        Path outputFile = Path.of(args[2]);

        if (!Files.exists(inputFile))
            System.err.println("Input file " + inputFile + " does not exists");

        System.out.println("Converting " + inputFile);

        StackExchangePostsDb.create(domain, outputFile, inputFile);

        System.out.println("... done!");
    }
}
