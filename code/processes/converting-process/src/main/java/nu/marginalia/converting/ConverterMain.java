package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.process.log.WorkLog;
import plan.CrawlPlanLoader;
import plan.CrawlPlan;
import nu.marginalia.converting.compiler.InstructionsCompiler;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.util.ParallelPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConverterMain {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InstructionWriter instructionWriter;

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
            CrawlPlan plan,
            DomainProcessor processor,
            InstructionsCompiler compiler,
            Gson gson
            ) throws Exception {
        logger.info("Starting pipe");

        try (WorkLog processLog = plan.createProcessWorkLog();
             ConversionLog log = new ConversionLog(plan.process.getDir())) {
            instructionWriter = new InstructionWriter(log, plan.process.getDir(), gson);
            var pipe = new ParallelPipe<CrawledDomain, ProcessingInstructions>("Converter", 16, 4, 2) {

                @Override
                protected ProcessingInstructions onProcess(CrawledDomain domainData) {
                    Thread.currentThread().setName("Converter:Processor["+domainData.domain+"] - " + domainData.size());
                    try {
                        var processed = processor.process(domainData);
                        var compiled = compiler.compile(processed);

                        return new ProcessingInstructions(domainData.id, compiled);
                    }
                    finally {
                        Thread.currentThread().setName("Converter:Processor[IDLE]");
                    }
                }

                @Override
                protected void onReceive(ProcessingInstructions processedInstructions) throws IOException {
                    Thread.currentThread().setName("Converter:Receiver["+processedInstructions.id+"]");
                    try {
                        var instructions = processedInstructions.instructions;
                        instructions.removeIf(Instruction::isNoOp);

                        String where = instructionWriter.accept(processedInstructions.id, instructions);
                        processLog.setJobToFinished(processedInstructions.id, where, instructions.size());
                    }
                    finally {
                        Thread.currentThread().setName("Converter:Receiver[IDLE]");
                    }
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
