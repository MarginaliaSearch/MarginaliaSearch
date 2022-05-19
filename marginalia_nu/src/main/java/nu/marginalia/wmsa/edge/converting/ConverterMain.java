package nu.marginalia.wmsa.edge.converting;

import com.google.gson.*;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.processor.DomainProcessor;
import nu.marginalia.wmsa.edge.converting.processor.InstructionsCompiler;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.CrawledDomainReader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.CrawlerSpecificationLoader;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConverterMain {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DomainProcessor processor;
    private final InstructionsCompiler compiler;
    private final WorkLog processLog;
    private final CrawledInstructionWriter instructionWriter;

    private final Gson gson;
    private final CrawledDomainReader reader = new CrawledDomainReader();

    private final Map<String, String> domainToId = new HashMap<>();
    private final Map<String, String> idToFileName = new HashMap<>();

    public static void main(String... args) throws IOException {

        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new ConverterModule(plan)
        );

        injector.getInstance(ConverterMain.class);
    }

    private static void requireArgs(String[] args, String... help) {
        if (args.length != help.length) {
            System.out.println("Usage: " + String.join(", ", help));
            System.exit(255);
        }
    }

    @Inject
    public ConverterMain(
            EdgeCrawlPlan plan,
            DomainProcessor processor,
            InstructionsCompiler compiler,
            Gson gson
            ) throws Exception {
        this.processor = processor;
        this.compiler = compiler;
        this.gson = gson;

        instructionWriter = new CrawledInstructionWriter(plan.process.getDir(), gson);

        logger.info("Loading input spec");
        CrawlerSpecificationLoader.readInputSpec(plan.getJobSpec(),
                spec -> domainToId.put(spec.domain, spec.id));

        logger.info("Replaying crawl log");
        WorkLog.readLog(plan.crawl.getLogFile(),
                entry -> idToFileName.put(entry.id(), entry.path()));

        logger.info("Starting pipe");
        processLog = new WorkLog(plan.process.getLogFile());


        var pipe = new ParallelPipe<CrawledDomain, ProcessingInstructions>("Crawler", 48, 4, 2) {
            @Override
            protected ProcessingInstructions onProcess(CrawledDomain domainData) {
                var processed = processor.process(domainData);
                return new ProcessingInstructions(domainData.id, compiler.compile(processed));
            }

            @Override
            protected void onReceive(ProcessingInstructions processedInstructions) throws IOException {
                var instructions = processedInstructions.instructions;
                instructions.removeIf(Instruction::isNoOp);

                String where = instructionWriter.accept(processedInstructions.id, instructions);
                processLog.setJobToFinished(processedInstructions.id, where, instructions.size());
            }
        };

        domainToId.forEach((domain, id) -> {
            String fileName = idToFileName.get(id);
            Path dest = getFilePath(plan.crawl.getDir(), fileName);
            logger.info("{} - {} - {}", domain, id, dest);

            if (!processLog.isJobFinished(id)) {
                try {
                    var cd = reader.read(dest);
                    pipe.accept(cd);

                } catch (IOException e) {
                    logger.error("Failed to read {}", dest);
                }
            }
        });

        pipe.join();

        processLog.close();

        logger.info("Finished");

        System.exit(0);
    }

    record ProcessingInstructions(String id, List<Instruction> instructions) {}

    private Path getFilePath(Path dir, String fileName) {
        String sp1 = fileName.substring(0, 2);
        String sp2 = fileName.substring(2, 4);
        return dir.resolve(sp1).resolve(sp2).resolve(fileName);
    }

}
