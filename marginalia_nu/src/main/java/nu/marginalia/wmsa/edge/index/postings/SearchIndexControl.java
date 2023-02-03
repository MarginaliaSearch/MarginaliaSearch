package nu.marginalia.wmsa.edge.index.postings;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.wmsa.edge.index.postings.journal.writer.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexSearchSetsService;
import nu.marginalia.wmsa.edge.index.svc.EdgeOpsLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@Singleton
public class SearchIndexControl {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final IndexServicesFactory servicesFactory;
    private final SearchIndexJournalWriterImpl primaryIndexWriter;
    private final SearchIndexJournalWriterImpl secondaryIndexWriter;
    private volatile KeywordLexiconReadOnlyView keywordLexiconReadOnlyView;

    private final SearchIndex index;
    private final EdgeOpsLockService opsLockService;

    @Inject
    public SearchIndexControl(IndexServicesFactory servicesFactory,
                              EdgeOpsLockService opsLockService,
                              EdgeIndexSearchSetsService searchSetsService) {
        this.servicesFactory = servicesFactory;

        this.primaryIndexWriter = servicesFactory.getIndexWriter(0);
        this.secondaryIndexWriter = servicesFactory.getIndexWriter(1);

        index = servicesFactory.createIndexBucket(searchSetsService);
        this.opsLockService = opsLockService;
    }

    public boolean reindex() throws Exception {
        return opsLockService.run(index::switchIndex).isPresent();
    }

    public boolean isBusy() {
        return opsLockService.isLocked();
    }

    @Nullable
    public KeywordLexiconReadOnlyView getLexiconReader() {
        return keywordLexiconReadOnlyView;
    }

    public void initialize(Initialization init) {

        logger.info("Waiting for init");
        init.waitReady();

        if (!opsLockService.run(index::init)) throw new IllegalStateException("Failed to initialize " + getClass().getSimpleName());
        keywordLexiconReadOnlyView = servicesFactory.getDictionaryReader();
    }

    public SearchIndexJournalWriterImpl getIndexWriter(int idx) {
        if (idx == 0) {
            return primaryIndexWriter;
        }
        else {
            return secondaryIndexWriter;
        }
    }

    public SearchIndex getIndex() {
        return index;
    }


}
