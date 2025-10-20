package nu.marginalia.index.reverse;

import nu.marginalia.index.reverse.query.EmptyEntrySource;
import nu.marginalia.index.reverse.query.EntrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PrioReverseIndexReader {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String name;

    private final Map<String, WordLexicon> wordLexiconMap;


    private final FileChannel documentsChannel;

    public PrioReverseIndexReader(String name,
                                  List<WordLexicon> wordLexicons,
                                  Path documents) throws IOException {
        this.name = name;

        if (!Files.exists(documents)) {
            this.documentsChannel = null;
            this.wordLexiconMap = Map.of();
            return;
        }

        wordLexiconMap = wordLexicons.stream().collect(Collectors.toUnmodifiableMap(lexicon -> lexicon.languageIsoCode, v -> v));
        documentsChannel = (FileChannel) Files.newByteChannel(documents);

        logger.info("Switching reverse index");
    }

    public EntrySource documents(IndexLanguageContext languageContext, String term, long termId) {
        if (languageContext.wordLexiconPrio == null) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource("prio", term);
        }

        long offset = languageContext.wordLexiconPrio.wordOffset(termId);

        if (offset < 0) // No documents
            return new EmptyEntrySource("prio", term);

        return new PrioIndexEntrySource(name,
                term,
                documentsChannel,
                offset);
    }

    /**
     * Return the number of documents with the termId in the index
     */
    public int numDocuments(IndexLanguageContext languageContext, long termId) {

        var lexicon = languageContext.wordLexiconPrio;
        if (null == lexicon)
            return 0;

        long offset = lexicon.wordOffset(termId);

        if (offset < 0) // No documents
            return 0;

        ByteBuffer buffer = ByteBuffer.allocate(4);
        try {
            documentsChannel.read(buffer, offset);
        } catch (IOException e) {
            logger.error("Failed to read documents channel", e);
            return 0;
        }

        return buffer.getInt(0) & 0x3FFF_FFFF;

    }


    public void close() {
        try {
            documentsChannel.close();
        } catch (IOException e) {
            logger.error("Failed to close documents channel", e);
        }

        wordLexiconMap.values().forEach(WordLexicon::close);
    }

    @Nullable
    public WordLexicon getWordLexicon(String languageIsoCode) {
        return wordLexiconMap.get(languageIsoCode);
    }
}