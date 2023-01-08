package nu.marginalia.wmsa.edge.index.postings;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.postings.forward.ForwardIndexReader;
import nu.marginalia.wmsa.edge.index.postings.forward.ParamMatchingQueryFilter;
import nu.marginalia.wmsa.edge.index.postings.reverse.ReverseIndexPrioReader;
import nu.marginalia.wmsa.edge.index.postings.reverse.ReverseIndexReader;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySourceBehavior;
import nu.marginalia.wmsa.edge.index.query.EntrySource;
import nu.marginalia.wmsa.edge.index.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterStepIf;
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

        return new IndexQueryBuilder(new IndexQuery(entrySources));
    }

    public IndexQueryBuilder findWordAsTopic(int[] wordIdsByFrequency) {
        List<EntrySource> entrySources = new ArrayList<>(wordIdsByFrequency.length);

        for (int wordId : wordIdsByFrequency) {
            entrySources.add(reverseIndexPrioReader.priorityDocuments(wordId));
        }

        return new IndexQueryBuilder(new IndexQuery(entrySources));
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

        return new IndexQueryBuilder(new IndexQuery(entrySources));
    }

    QueryFilterStepIf filterForParams(IndexQueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }
    @SneakyThrows
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

    public class IndexQueryBuilder {
        private final IndexQuery query;

        IndexQueryBuilder(IndexQuery query) {
            this.query = query;
        }

        public IndexQueryBuilder also(int termId) {

            query.addInclusionFilter(reverseIndexReader.also(termId));

            return this;
        }

        public IndexQueryBuilder not(int termId) {

            query.addInclusionFilter(reverseIndexReader.not(termId));

            return this;
        }

        public IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep) {

            query.addInclusionFilter(filterStep);

            return this;
        }

        public IndexQuery build() {
            return query;
        }

    }
}
