package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.config.IndexFileName;
import nu.marginalia.index.config.ReverseIndexParameters;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.reverse.FullReverseIndexReader;
import nu.marginalia.index.reverse.PrioReverseIndexReader;
import nu.marginalia.btree.paged.PagedBTreeWriter;
import nu.marginalia.index.reverse.WordLexicon;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class IndexFactory {
    private final FileStorageService fileStorageService;
    private final Path liveStorage;
    private final LanguageConfiguration languageConfiguration;
    private static final Logger logger = LoggerFactory.getLogger(IndexFactory.class);

    @Inject
    public IndexFactory(FileStorageService fileStorageService, LanguageConfiguration languageConfiguration) {

        this.fileStorageService = fileStorageService;
        this.liveStorage = IndexLocations.getCurrentIndex(fileStorageService);
        this.languageConfiguration = languageConfiguration;
    }

    public CombinedIndexReader getCombinedIndexReader() throws IOException {
        return new CombinedIndexReader(
                getForwardIndexReader(),
                getReverseIndexReader(),
                getReverseIndexPrioReader()
        );
    }

    public Path getSearchSetsBase() {
        return IndexLocations.getSearchSetsPath(fileStorageService);
    }

    public FullReverseIndexReader getReverseIndexReader() throws IOException {

        Path docsFile = getCurrentPath(new IndexFileName.FullDocs());
        Path docsValuesFile = getCurrentPath(new IndexFileName.FullDocsValues());
        Path positionsFile = getCurrentPath(new IndexFileName.FullPositions());

        List<WordLexicon> wordLexicons = new ArrayList<>();

        for (LanguageDefinition languageDefinition : languageConfiguration.languages()) {
            String languageIsoCode = languageDefinition.isoCode();
            Path wordsFile = getCurrentPath(new IndexFileName.FullWords(languageIsoCode));
            if (Files.exists(wordsFile)) {
                wordLexicons.add(openWordLexicon(languageIsoCode, wordsFile));
            }
            else if ("en".equalsIgnoreCase(languageIsoCode)) {
                // FIXME:  Backward compatibility, remove after ~ dec 2025
                wordsFile = liveStorage.resolve("rev-words.dat");
                wordLexicons.add(WordLexicon.openLegacy("en", wordsFile));
            }
        }


        return new FullReverseIndexReader("full",
                wordLexicons,
                docsFile,
                docsValuesFile,
                positionsFile
        );
    }

    public PrioReverseIndexReader getReverseIndexPrioReader() throws IOException {

        List<WordLexicon> wordLexicons = new ArrayList<>();

        for (LanguageDefinition languageDefinition : languageConfiguration.languages()) {
            String languageIsoCode = languageDefinition.isoCode();
            Path wordsFile = getCurrentPath(new IndexFileName.PrioWords(languageIsoCode));
            if (Files.exists(wordsFile)) {
                wordLexicons.add(openWordLexicon(languageIsoCode, wordsFile));
            }
            else if ("en".equalsIgnoreCase(languageIsoCode)) {
                // FIXME:  Backward compatibility, remove after ~ dec 2025
                wordsFile = liveStorage.resolve("rev-prio-words.dat");
                wordLexicons.add(WordLexicon.openLegacy("en", wordsFile));
            }
        }

        Path docsFile = getCurrentPath(new IndexFileName.PrioDocs());

        return new PrioReverseIndexReader("prio", wordLexicons, docsFile);
    }

    public ForwardIndexReader getForwardIndexReader() throws IOException {
        Path docIdsFile = getCurrentPath(new IndexFileName.ForwardDocIds());
        Path docDataFile = getCurrentPath(new IndexFileName.ForwardDocData());
        Path spansFile = getCurrentPath(new IndexFileName.ForwardSpansData());

        return new ForwardIndexReader(docIdsFile, docDataFile, spansFile);
    }

    private static final boolean useBuffered = Boolean.getBoolean("index.wordLexicon.useBuffered");
    private static final int poolSizeBytes = Integer.getInteger("index.wordLexicon.poolSizeBytes", 64 * 1024 * 1024);

    /** Open a WordLexicon, detecting the file format from the magic number. */
    private WordLexicon openWordLexicon(String languageIsoCode, Path wordsFile) throws IOException {
        if (!isPagedBTreeFormat(wordsFile)) {
            return WordLexicon.openLegacy(languageIsoCode, wordsFile);
        }

        if (useBuffered) {
            return WordLexicon.openBuffered(languageIsoCode, wordsFile);
        }

        int poolSizePages = poolSizeBytes / ReverseIndexParameters.BTREE_PAGE_SIZE_BYTES;
        return WordLexicon.openDirect(languageIsoCode, wordsFile, poolSizePages);
    }

    private static boolean isPagedBTreeFormat(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            if (ch.size() < 4) return false;
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
            ch.read(buf, 0);
            buf.flip();
            return buf.getInt() == PagedBTreeWriter.MAGIC;
        }
    }

    private Path getCurrentPath(IndexFileName fileName) {
        return IndexFileName.resolve(liveStorage, fileName, IndexFileName.Version.CURRENT);
    }

    /** Switches the current index to the next index */
    public void switchFiles() throws IOException {

        for (var file : IndexFileName.forwardIndexFiles()) {
            switchFile(
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.NEXT),
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.CURRENT)
            );
        }

        for (IndexFileName file : IndexFileName.revPrioIndexFiles(languageConfiguration)) {
            switchFile(
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.NEXT),
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.CURRENT)
            );
        }

        for (IndexFileName file : IndexFileName.revFullIndexFiles(languageConfiguration)) {
            switchFile(
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.NEXT),
                    IndexFileName.resolve(liveStorage, file, IndexFileName.Version.CURRENT)
            );
        }
    }

    public void switchFile(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            logger.info("Switching {} -> {} ({}b)", from.getFileName(), to.getFileName(), Files.size(from));
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }


}
