package nu.marginalia.wmsa.edge.tools;

import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;

import java.io.IOException;
import java.nio.file.Path;

public class IndexJournalDumpTool {
    public static void main(String... args) throws IOException {
        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(Path.of(args[0])));
        for (var entry : reader) {
            System.out.printf("%s\t%010d\t%06d:%08d\n", entry.block(), entry.docId(), entry.domainId(), entry.urlId());
        }
    }
}
