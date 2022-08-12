package nu.marginalia.wmsa.edge.tools;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.edge.converting.ConverterModule;
import nu.marginalia.wmsa.edge.converting.processor.DomainProcessor;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ConverterLogicTestTool {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String... args) throws IOException {

        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new ConverterModule(plan)
        );

        injector.getInstance(ConverterLogicTestTool.class);
    }

    @Inject
    public ConverterLogicTestTool(
            EdgeCrawlPlan plan,
            DomainProcessor processor
            ) throws Exception {

        plan.forEachCrawledDomain(domain -> {
            var ret = processor.process(domain);
            ret.documents.forEach(doc -> {
                if (doc.words == null)
                    return;
                var artifacts = doc.words.get(IndexBlock.Artifacts);
                if (artifacts.size() > 0) {
                    System.out.println(doc.url + ": " + artifacts);
                }
            });
        });

    }

}
