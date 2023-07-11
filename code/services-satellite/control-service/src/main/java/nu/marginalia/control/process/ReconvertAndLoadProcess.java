package nu.marginalia.control.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.search.client.SearchClient;
import nu.marginalia.search.client.SearchMqEndpoints;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Singleton
public class ReconvertAndLoadProcess extends AbstractStateGraph {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String RECONVERT = "RECONVERT";
    private static final String LOAD = "LOAD";
    private static final String MOVE_INDEX_FILES = "MOVE_INDEX_FILES";
    private static final String RELOAD_LEXICON = "RELOAD_LEXICON";
    private static final String RELOAD_LEXICON_WAIT = "RELOAD_LEXICON_WAIT";
    private static final String FLUSH_CACHES = "FLUSH_CACHES";
    private static final String END = "END";
    private final ProcessService processService;
    private final MqOutbox mqIndexOutbox;
    private final MqOutbox mqSearchOutbox;


    @Inject
    public ReconvertAndLoadProcess(StateFactory stateFactory,
                                   ProcessService processService,
                                   IndexClient indexClient,
                                   SearchClient searchClient
                                   ) {
        super(stateFactory);
        this.processService = processService;
        this.mqIndexOutbox = indexClient.outbox();
        this.mqSearchOutbox = searchClient.outbox();
    }

    @GraphState(name = INITIAL, next = RECONVERT)
    public String init(String crawlJob) throws Exception {
        Path path = Path.of(crawlJob);

        if (!Files.exists(path)) {
            error("Bad crawl job path");
        }

        Files.deleteIfExists(path.getParent().resolve("process/process.log"));

        return path.toString();
    }

    @GraphState(name = RECONVERT, next = LOAD, resume = ResumeBehavior.RETRY)
    public String reconvert(String crawlJob) throws Exception {
        if (!processService.trigger(ProcessService.ProcessId.CONVERTER, Path.of(crawlJob)))
            error();

        return crawlJob;
    }

    @GraphState(name = LOAD, next = MOVE_INDEX_FILES, resume = ResumeBehavior.RETRY)
    public void load(String crawlJob) throws Exception {
        if (!processService.trigger(ProcessService.ProcessId.LOADER, Path.of(crawlJob)))
            error();
    }

    @GraphState(name = MOVE_INDEX_FILES, next = RELOAD_LEXICON, resume = ResumeBehavior.ERROR)
    public void moveIndexFiles(String crawlJob) throws Exception {
        Path indexData = Path.of("/vol/index.dat");
        Path indexDest = Path.of("/vol/iw/0/page-index.dat");

        if (!Files.exists(indexData))
            error("Index data not found");

        Files.move(indexData, indexDest, StandardCopyOption.REPLACE_EXISTING);
    }

    @GraphState(name = RELOAD_LEXICON, next = RELOAD_LEXICON_WAIT, resume = ResumeBehavior.ERROR)
    public long reloadLexicon() throws Exception {
        return mqIndexOutbox.sendAsync(IndexMqEndpoints.INDEX_RELOAD_LEXICON, "");
    }

    @GraphState(name = RELOAD_LEXICON_WAIT, next = FLUSH_CACHES, resume = ResumeBehavior.RETRY)
    public void reloadLexiconWait(long id) throws Exception {
        var rsp = mqIndexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("RELOAD_LEXICON failed");
        }
    }

    @GraphState(name = FLUSH_CACHES, next = END, resume = ResumeBehavior.RETRY)
    public void flushCaches() throws Exception {
        var rsp = mqSearchOutbox.send(SearchMqEndpoints.FLUSH_CACHES, "");

        if (rsp.state() != MqMessageState.OK) {
            error("FLUSH_CACHES failed");
        }
    }
}
