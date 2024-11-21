package nu.marginalia.crawl.warc;

import com.google.inject.Inject;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.process.ProcessConfiguration;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Factory for creating WarcArchiverIf instances.  Depending on the node's configuration,
 * either a shredder instance that just discards the Warc file, or a persisting instance
 * that creates a series of concatenated warc.gz-files with an index
 */
public class WarcArchiverFactory {
    private final boolean keepWarcs;

    @Inject
    public WarcArchiverFactory(ProcessConfiguration processConfiguration,
                               NodeConfigurationService nodeConfigurationService)
    throws Exception
    {
        keepWarcs = nodeConfigurationService.get(processConfiguration.node()).keepWarcs();
    }

    public WarcArchiverIf get(Path outputDir) throws IOException {
        if (!keepWarcs) {
            return new WarcArchiverShredder();
        } else {
            return new WarcArchiver(outputDir);
        }
    }

}

/** Dummy archiver that just deletes the warc file. */
class WarcArchiverShredder implements WarcArchiverIf {
    @Override
    public void consumeWarc(Path warcFile, String domain) throws IOException {
        Files.deleteIfExists(warcFile);
    }

    @Override
    public void close() {}
}

/** Archives warc files to disk.  Concatenates all warc files into a single
 * warc file, and creates an index file with the offsets and lengths of
 * each domain segment.
 * */
class WarcArchiver implements WarcArchiverIf {
    // Specs say the recommended maximum size of a warc file is ~1GB, after which a new file should be created
    private static final long MAX_COMBINED_WARC_FILE_SIZE = 1_000_000_000;


    private PrintWriter indexWriter;
    private OutputStream warcWriter;
    private final Path warcDir;

    String warcFileName = null;
    String ts = LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .replace(':', '-');

    long pos = 0;
    int fileCounter = 0;

    public WarcArchiver(Path outputDir) throws IOException {
       warcDir = outputDir.resolve("warc");

        if (!Files.exists(warcDir)) {
            Files.createDirectories(warcDir);
        }

        switchFile();
    }

    private void switchFile() throws IOException {
        if (warcWriter != null) warcWriter.close();

        warcFileName = "marginalia-crawl-" + ts + "--" + String.format("%04d", fileCounter++) + ".warc.gz";

        warcWriter = Files.newOutputStream(warcDir.resolve(warcFileName));

        if (indexWriter == null) {
            Path indexFile = warcDir.resolve("marginalia-crawl-" + ts + ".idx");
            indexWriter = new PrintWriter(Files.newBufferedWriter(indexFile));
        }
    }

    @Override
    public void consumeWarc(Path warcFile, String domain) throws IOException {
        try {
            synchronized (this) {
                // Specs say the recommended maximum size of a warc file is ~1GB
                if (pos > MAX_COMBINED_WARC_FILE_SIZE) {
                    switchFile();
                }

                indexWriter.printf("%s %s %d %d\n", warcFileName, domain, pos, Files.size(warcFile));
                indexWriter.flush();
                try (var is = Files.newInputStream(warcFile)) {
                    pos += IOUtils.copy(is, warcWriter);
                }
            }
        }
        finally {
            Files.deleteIfExists(warcFile);
        }
    }

    @Override
    public void close() throws IOException {
        if (warcWriter != null) warcWriter.close();
        if (indexWriter != null) indexWriter.close();
    }
}