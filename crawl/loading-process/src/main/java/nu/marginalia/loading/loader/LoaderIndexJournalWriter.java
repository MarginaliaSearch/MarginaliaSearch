package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.dict.DictionaryMap;
import nu.marginalia.dict.OffHeapDictionaryHashMap;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.writer.IndexJournalWriterImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.model.crawl.DocumentKeywords;
import nu.marginalia.util.KeywordListChunker;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

@Singleton
public class LoaderIndexJournalWriter {

    private final KeywordLexicon lexicon;
    private final IndexJournalWriter indexWriter;
    private static final Logger logger = LoggerFactory.getLogger(LoaderIndexJournalWriter.class);

    @Inject
    public LoaderIndexJournalWriter(@Named("local-index-path") Path path) throws IOException {

        var lexiconJournal = new KeywordLexiconJournal(path.resolve("dictionary.dat").toFile());
        lexicon = new KeywordLexicon(lexiconJournal);
        indexWriter = new IndexJournalWriterImpl(lexicon, path.resolve("index.dat"));
    }

    public void putWords(EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url,
                         EdgePageDocumentsMetadata metadata,
                         DocumentKeywords wordSet) {
        if (wordSet.keywords().length == 0)
            return;

        if (domain.id() <= 0 || url.id() <= 0) {
            logger.warn("Bad ID: {}:{}", domain, url);
            return;
        }

        for (var chunk : KeywordListChunker.chopList(wordSet, IndexJournalEntryData.MAX_LENGTH)) {

            var entry = new IndexJournalEntryData(getOrInsertWordIds(chunk.keywords(), chunk.metadata()));
            var header = new IndexJournalEntryHeader(domain, url, metadata.encode());

            indexWriter.put(header, entry);
        }

    }

    private long[] getOrInsertWordIds(String[] words, long[] meta) {
        long[] ids = new long[words.length*2];
        int putIdx = 0;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            long id = lexicon.getOrInsert(word);
            if (id != OffHeapDictionaryHashMap.NO_VALUE) {
                ids[putIdx++] = id;
                ids[putIdx++] = meta[i];
            }
        }

        if (putIdx != words.length*2) {
            ids = Arrays.copyOf(ids, putIdx);
        }
        return ids;
    }

    public void close() throws Exception {
        indexWriter.close();
        lexicon.close();
    }
}
