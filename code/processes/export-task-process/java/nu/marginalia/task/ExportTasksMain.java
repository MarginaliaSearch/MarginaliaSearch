package nu.marginalia.task;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.adjacencies.WebsiteAdjacenciesCalculator;
import nu.marginalia.extractor.AtagExporter;
import nu.marginalia.extractor.FeedExporter;
import nu.marginalia.extractor.SampleDataExporter;
import nu.marginalia.extractor.TermFrequencyExporter;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.tasks.ExportTaskRequest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportTasksMain extends ProcessMainClass {

    private static final Logger logger = LoggerFactory.getLogger(ExportTasksMain.class);

    private final AtagExporter atagExporter;
    private final FeedExporter feedExporter;
    private final SampleDataExporter sampleDataExporter;
    private final TermFrequencyExporter termFrequencyExporter;
    private final WebsiteAdjacenciesCalculator websiteAdjacenciesCalculator;
    private final ProcessHeartbeat heartbeat;

    public static void main(String[] args) throws Exception {

        var injector = Guice.createInjector(
                new ServiceDiscoveryModule(),
                new ProcessConfigurationModule("export-tasks"),
                new DatabaseModule(false)
        );

        var exportTasks = injector.getInstance(ExportTasksMain.class);

        Instructions<ExportTaskRequest> instructions = exportTasks.fetchInstructions(ExportTaskRequest.class);
        try {
            exportTasks.run(instructions.value());
            instructions.ok();
        }
        catch (Exception e) {
            logger.error("Error running export task", e);
            instructions.err();
        }

    }

    @Inject
    public ExportTasksMain(MessageQueueFactory messageQueueFactory,
                           ProcessConfiguration config,
                           AtagExporter atagExporter,
                           FeedExporter feedExporter,
                           SampleDataExporter sampleDataExporter,
                           TermFrequencyExporter termFrequencyExporter,
                           Gson gson,
                           WebsiteAdjacenciesCalculator websiteAdjacenciesCalculator, ProcessHeartbeat heartbeat)
    {
        super(messageQueueFactory, config, gson, ProcessInboxNames.EXPORT_TASK_INBOX);
        this.atagExporter = atagExporter;
        this.feedExporter = feedExporter;
        this.sampleDataExporter = sampleDataExporter;
        this.termFrequencyExporter = termFrequencyExporter;
        this.websiteAdjacenciesCalculator = websiteAdjacenciesCalculator;
        this.heartbeat = heartbeat;
    }

    enum ProcessSteps {
        RUN,
        END
    }

    private void run(ExportTaskRequest request) throws Exception {
        try (var hb = heartbeat.createProcessTaskHeartbeat(ProcessSteps.class, request.task.toString())) {
            hb.progress(ProcessSteps.RUN);

            switch (request.task) {
                case ATAGS:
                    atagExporter.export(request.crawlId, request.destId);
                    break;
                case FEEDS:
                    feedExporter.export(request.crawlId, request.destId);
                    break;
                case TERM_FREQ:
                    termFrequencyExporter.export(request.crawlId, request.destId);
                    break;
                case SAMPLE_DATA:
                    sampleDataExporter.export(request.crawlId, request.destId, request.size, request.ctFilter, request.name);
                    break;
                case ADJACENCIES:
                    websiteAdjacenciesCalculator.export();
                    break;
            }

            hb.progress(ProcessSteps.RUN);
        }
    }


}
