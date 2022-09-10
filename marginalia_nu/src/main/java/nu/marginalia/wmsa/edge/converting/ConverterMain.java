package nu.marginalia.wmsa.edge.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.processor.DomainProcessor;
import nu.marginalia.wmsa.edge.converting.processor.InstructionsCompiler;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.WorkLog;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConverterMain {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LoadInstructionWriter instructionWriter;

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

    @Inject
    public ConverterMain(
            EdgeCrawlPlan plan,
            DomainProcessor processor,
            InstructionsCompiler compiler,
            Gson gson
            ) throws Exception {

        instructionWriter = new LoadInstructionWriter(plan.process.getDir(), gson);

        logger.info("Starting pipe");

        try (WorkLog processLog = plan.createProcessWorkLog()) {
            var pipe = new ParallelPipe<CrawledDomain, ProcessingInstructions>("Crawler", 20, 4, 2) {

                @Override
                protected ProcessingInstructions onProcess(CrawledDomain domainData) {
                    var processed = processor.process(domainData);
                    var compiled = compiler.compile(processed);

                    return new ProcessingInstructions(domainData.id, compiled);
                }

                @Override
                protected void onReceive(ProcessingInstructions processedInstructions) throws IOException {
                    var instructions = processedInstructions.instructions;
                    instructions.removeIf(Instruction::isNoOp);

                    String where = instructionWriter.accept(processedInstructions.id, instructions);
                    processLog.setJobToFinished(processedInstructions.id, where, instructions.size());
                }

            };

            plan.forEachCrawledDomain(id -> !processLog.isJobFinished(id), pipe::accept);

            pipe.join();
        }

        logger.info("Finished");

        System.exit(0);
    }

    record ProcessingInstructions(String id, List<Instruction> instructions) {}

}
