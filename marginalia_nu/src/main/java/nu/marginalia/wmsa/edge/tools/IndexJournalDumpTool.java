package nu.marginalia.wmsa.edge.tools;

import com.google.common.hash.Hashing;
import net.agkn.hll.HLL;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;

import java.io.IOException;
import java.nio.file.Path;

public class IndexJournalDumpTool {
    public static void main(String... args) throws IOException {
        final String operation = args.length > 0 ? args[0] : "help";

        switch (operation) {
            case "dump":
                dump(Path.of(args[1]));
                break;
            case "cardinality":
                cardinality(Path.of(args[1]));
                break;
            default:
                System.err.println("Usage: dump|cardinality index-file.dat");
                break;
        }

    }

    private static void cardinality(Path file) throws IOException {
        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(file));
        HLL hyperloglog = new HLL(30, 1);
        var hashFunction = Hashing.murmur3_128();

        for (var entry : reader) {
            hyperloglog.addRaw(hashFunction.hashLong(entry.docId()).padToLong());
        }

        System.out.println(hyperloglog.cardinality());
    }

    private static void dump(Path file) throws IOException {
        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(file));
        for (var entry : reader) {
            System.out.printf("%s\t%010d\t%06d:%08d\n", entry.block(), entry.docId(), entry.domainId(), entry.urlId());
        }
    }
}
