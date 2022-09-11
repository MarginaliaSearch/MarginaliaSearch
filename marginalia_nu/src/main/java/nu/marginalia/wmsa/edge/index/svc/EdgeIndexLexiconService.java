package nu.marginalia.wmsa.edge.index.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import nu.marginalia.util.ListChunker;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.wmsa.wmsa.edge.index.proto.IndexPutKeywordsReq;
import org.apache.http.HttpStatus;
import spark.Request;
import spark.Response;

import java.util.Arrays;
import java.util.List;

@Singleton
public class EdgeIndexLexiconService {

    private final SearchIndexes indexes;
    private final KeywordLexicon keywordLexicon;

    @Inject
    public EdgeIndexLexiconService(SearchIndexes indexes, IndexServicesFactory servicesFactory) {
        this.indexes = indexes;
        this.keywordLexicon = servicesFactory.getKeywordLexicon();
    }

    public Object getWordId(Request request, Response response) {
        final String word = request.splat()[0];

        var lr = indexes.getLexiconReader();
        if (null == lr) {
            response.status(HttpStatus.SC_FAILED_DEPENDENCY);
            return "";
        }

        final int wordId = lr.get(word);

        if (DictionaryHashMap.NO_VALUE == wordId) {
            response.status(404);
            return "";
        }

        return wordId;
    }


    public Object putWords(Request request, Response response) throws InvalidProtocolBufferException {
        var req = IndexPutKeywordsReq.parseFrom(request.bodyAsBytes());

        EdgeId<EdgeDomain> domainId = new EdgeId<>(req.getDomain());
        EdgeId<EdgeUrl> urlId = new EdgeId<>(req.getUrl());
        int idx = req.getIndex();

        for (int ws = 0; ws < req.getWordSetCount(); ws++) {
            putWords(domainId, urlId, req.getWordSet(ws), idx);
        }

        response.status(HttpStatus.SC_ACCEPTED);
        return "";
    }

    public void putWords(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId,
                         IndexPutKeywordsReq.WordSet words, int idx
    ) {
        SearchIndexJournalWriterImpl indexWriter = indexes.getIndexWriter(idx);

        IndexBlock block = IndexBlock.values()[words.getIndex()];

        for (var chunk : ListChunker.chopList(words.getWordsList(), SearchIndexJournalEntry.MAX_LENGTH)) {

            var entry = new SearchIndexJournalEntry(getOrInsertWordIds(chunk));
            var header = new SearchIndexJournalEntryHeader(domainId, urlId, block);

            indexWriter.put(header, entry);
        };
    }

    private long[] getOrInsertWordIds(List<String> words) {
        long[] ids = new long[words.size()];
        int putIdx = 0;

        for (String word : words) {
            long id = keywordLexicon.getOrInsert(word);
            if (id != DictionaryHashMap.NO_VALUE) {
                ids[putIdx++] = id;
            }
        }

        if (putIdx != words.size()) {
            ids = Arrays.copyOf(ids, putIdx);
        }
        return ids;
    }


}
