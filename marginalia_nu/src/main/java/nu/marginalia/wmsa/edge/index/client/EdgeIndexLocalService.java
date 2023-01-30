package nu.marginalia.wmsa.edge.index.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.util.dict.OffHeapDictionaryHashMap;
import nu.marginalia.util.dict.DictionaryMap;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.KeywordListChunker;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.postings.journal.writer.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

@Singleton
public class EdgeIndexLocalService implements EdgeIndexWriterClient {

    private final KeywordLexicon lexicon;
    private final SearchIndexJournalWriterImpl indexWriter;
    private static final Logger logger = LoggerFactory.getLogger(EdgeIndexLocalService.class);

    @Inject
    public EdgeIndexLocalService(@Named("local-index-path") Path path) throws IOException {

        var lexiconJournal = new KeywordLexiconJournal(path.resolve("dictionary.dat").toFile());
        lexicon = new KeywordLexicon(lexiconJournal, DictionaryMap.create());
        indexWriter = new SearchIndexJournalWriterImpl(lexicon, path.resolve("index.dat").toFile());
    }

    public void putWords(Context ctx, EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url,
                         EdgePageDocumentsMetadata metadata,
                         DocumentKeywords wordSet, int writer) {
        if (wordSet.keywords().length == 0)
            return;

        if (domain.id() <= 0 || url.id() <= 0) {
            logger.warn("Bad ID: {}:{}", domain, url);
            return;
        }

        for (var chunk : KeywordListChunker.chopList(wordSet, SearchIndexJournalEntry.MAX_LENGTH)) {

            var entry = new SearchIndexJournalEntry(getOrInsertWordIds(chunk.keywords(), chunk.metadata()));
            var header = new SearchIndexJournalEntryHeader(domain, url, metadata.encode());

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

    @Override
    public void close() throws Exception {
        indexWriter.close();
        lexicon.close();
    }
}
