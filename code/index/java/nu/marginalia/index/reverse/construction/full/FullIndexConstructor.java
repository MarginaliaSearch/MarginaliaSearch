package nu.marginalia.index.reverse.construction.full;

import com.google.common.io.MoreFiles;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.process.control.ProcessHeartbeat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FullIndexConstructor {
    public enum CreateReverseIndexSteps {
        CONSTRUCT,
        MERGE,
        FINALIZE,
        FINISHED
    }

    private final Path outputFileDocs;
    private final Path outputFileDocsValues;
    private final Path outputFileWords;
    private final Path outputFilePositions;
    private final DocIdRewriter docIdRewriter;
    private final Path tmpDir;

    public FullIndexConstructor(Path outputFileDocs,
                                Path outputFileDocsValues,
                                Path outputFileWords,
                                Path outputFilePositions,
                                DocIdRewriter docIdRewriter,
                                Path tmpDir)
    {
        this.outputFileDocs = outputFileDocs;
        this.outputFileDocsValues = outputFileDocsValues;
        this.outputFileWords = outputFileWords;
        this.outputFilePositions = outputFilePositions;
        this.docIdRewriter = docIdRewriter;
        this.tmpDir = tmpDir;
    }

    public void createReverseIndex(ProcessHeartbeat processHeartbeat,
                                   String processName,
                                   IndexJournal journal) throws IOException
    {
        int workers = Integer.getInteger("index.fullConstructionThreads", Math.min(4, Runtime.getRuntime().availableProcessors()));
        if (workers < 1) {
            throw new IllegalArgumentException("Index construction needs at least one worker");
        }

        Path workDir = Files.createTempDirectory(tmpDir, "full-index-");
        try {
            construct(processHeartbeat, processName, journal, workDir, workers);
        }
        catch (IOException | RuntimeException | Error e) {
            try {
                MoreFiles.deleteRecursively(workDir);
            }
            catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        MoreFiles.deleteRecursively(workDir);
    }

    private void construct(ProcessHeartbeat processHeartbeat,
                           String processName,
                           IndexJournal journal,
                           Path workDir,
                           int workers) throws IOException
    {
        boolean compress = FullPreindex.compressionEnabled();

        try (var heartbeat = processHeartbeat.createProcessTaskHeartbeat(CreateReverseIndexSteps.class, processName);
             var preindexHeartbeat = processHeartbeat.createAdHocTaskHeartbeat("constructPreindexes");
             var executor = Executors.newFixedThreadPool(workers))
        {
            heartbeat.progress(CreateReverseIndexSteps.CONSTRUCT);

            List<Callable<List<FullPreindexReference>>> tasks = new ArrayList<>();

            List<IndexJournalPage> pages = journal.pages();

            AtomicInteger progress = new AtomicInteger();
            for (var page : pages) {
                tasks.add(() -> {
                    var result = FullPreindexBuilder.constructRuns(page, docIdRewriter, workDir, compress);
                    preindexHeartbeat.progress("PREINDEX", progress.incrementAndGet(), pages.size());
                    return result;
                });
            }

            List<FullPreindexReference> runs = new ArrayList<>();
            for (var future : executor.invokeAll(tasks)) {
                runs.addAll(future.get());
            }

            heartbeat.progress(CreateReverseIndexSteps.MERGE);
            var merged = FullPreindex.mergeAll(workDir, runs, compress, executor);

            if (merged.isPresent()) {
                heartbeat.progress(CreateReverseIndexSteps.FINALIZE);
                try (var preindex = merged.get().open()) {
                    preindex.finalizeIndex(outputFileDocs, outputFileDocsValues, outputFileWords, outputFilePositions);
                }
            }
            heartbeat.progress(CreateReverseIndexSteps.FINISHED);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var failure = new InterruptedIOException("Index construction interrupted");
            failure.initCause(e);
            throw failure;
        }
        catch (UncheckedIOException|ExecutionException e) {
            throw new IOException("Index construction failed", e.getCause());
        }
    }

}
