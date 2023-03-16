package nu.marginalia.index.index;

import lombok.SneakyThrows;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.ParamMatchingQueryFilter;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.reverse.ReverseIndexPrioReader;
import nu.marginalia.index.reverse.ReverseIndexReader;
import nu.marginalia.index.reverse.query.ReverseIndexEntrySourceBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SearchIndexReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ForwardIndexReader forwardIndexReader;
    private final ReverseIndexReader reverseIndexReader;
    private final ReverseIndexPrioReader reverseIndexPrioReader;

    public SearchIndexReader(ForwardIndexReader forwardIndexReader,
                             ReverseIndexReader reverseIndexReader,
                             ReverseIndexPrioReader reverseIndexPrioReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexReader = reverseIndexReader;
        this.reverseIndexPrioReader = reverseIndexPrioReader;
    }

    public IndexQueryBuilder findWordAsSentence(int[] wordIdsByFrequency) {
        List<EntrySource> entrySources = new ArrayList<>(1);

        entrySources.add(reverseIndexReader.documents(wordIdsByFrequency[0], ReverseIndexEntrySourceBehavior.DO_PREFER));

        return new SearchIndexQueryBuilder(reverseIndexReader, new IndexQuery(entrySources));
    }

    public IndexQueryBuilder findWordAsTopic(int[] wordIdsByFrequency) {
        List<EntrySource> entrySources = new ArrayList<>(wordIdsByFrequency.length);

        for (int wordId : wordIdsByFrequency) {
            entrySources.add(reverseIndexPrioReader.priorityDocuments(wordId));
        }

        return new SearchIndexQueryBuilder(reverseIndexReader, new IndexQuery(entrySources));
    }

    public IndexQueryBuilder findWordTopicDynamicMode(int[] wordIdsByFrequency) {
        if (wordIdsByFrequency.length > 3) {
            return findWordAsSentence(wordIdsByFrequency);
        }

        List<EntrySource> entrySources = new ArrayList<>(wordIdsByFrequency.length + 1);

        for (int wordId : wordIdsByFrequency) {
            entrySources.add(reverseIndexPrioReader.priorityDocuments(wordId));
        }

        entrySources.add(reverseIndexReader.documents(wordIdsByFrequency[0], ReverseIndexEntrySourceBehavior.DO_NOT_PREFER));

        return new SearchIndexQueryBuilder(reverseIndexReader, new IndexQuery(entrySources));
    }

    QueryFilterStepIf filterForParams(IndexQueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    public long numHits(int word) {
        return reverseIndexReader.numDocuments(word);
    }

    public long[] getMetadata(int wordId, long[] docIds) {
        return reverseIndexReader.getTermMeta(wordId, docIds);
    }

    public long getDocumentMetadata(long docId) {
        return forwardIndexReader.getDocMeta(docId);
    }

    public int getDomainId(long docId) {
        return forwardIndexReader.getDomainId(docId);
    }

    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }
}
