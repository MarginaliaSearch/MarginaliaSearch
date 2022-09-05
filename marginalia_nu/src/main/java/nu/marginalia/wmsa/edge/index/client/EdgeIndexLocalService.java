package nu.marginalia.wmsa.edge.index.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.util.ListChunker;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DocumentKeywords;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Singleton
public class EdgeIndexLocalService implements EdgeIndexWriterClient {

    private final KeywordLexicon lexicon;
    private final SearchIndexJournalWriterImpl indexWriter;

    @Inject
    public EdgeIndexLocalService(@Named("local-index-path") Path path) throws IOException {
        long hashMapSize = 1L << 31;

        if (Boolean.getBoolean("small-ram")) {
            hashMapSize = 1L << 27;
        }

        var lexiconJournal = new KeywordLexiconJournal(path.resolve("dictionary.dat").toFile());
        lexicon = new KeywordLexicon(lexiconJournal, new DictionaryHashMap(hashMapSize));
        indexWriter = new SearchIndexJournalWriterImpl(lexicon, path.resolve("index.dat").toFile());
    }

    public void putWords(Context ctx, EdgeId<EdgeDomain> domain, EdgeId<EdgeUrl> url,
                         DocumentKeywords wordSet, int writer) {
        if (wordSet.keywords().length == 0) return;

        for (var chunk : ListChunker.chopList(List.of(wordSet.keywords()), SearchIndexJournalEntry.MAX_LENGTH)) {

            var entry = new SearchIndexJournalEntry(getOrInsertWordIds(chunk));
            var header = new SearchIndexJournalEntryHeader(domain.id(), url.id(), wordSet.block());

            indexWriter.put(header, entry);
        }

    }

    private long[] getOrInsertWordIds(List<String> words) {
        long[] ids = new long[words.size()];
        int putId = 0;

        for (String word : words) {
            long id = lexicon.getOrInsert(word);
            if (id != DictionaryHashMap.NO_VALUE) {
                ids[putId++] = id;
            }
        }

        if (putId != words.size()) {
            ids = Arrays.copyOf(ids, putId);
        }
        return ids;
    }

    @Override
    public void close() throws Exception {
        indexWriter.close();
        lexicon.close();
    }
}
