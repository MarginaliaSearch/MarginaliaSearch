package nu.marginalia.wmsa.edge.tools;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.converting.ConverterModule;
import nu.marginalia.wmsa.edge.converting.processor.DomainProcessor;
import nu.marginalia.wmsa.edge.converting.processor.logic.DomPruner;
import nu.marginalia.wmsa.edge.converting.processor.logic.topic.RecipeDetector;
import nu.marginalia.wmsa.edge.converting.processor.logic.topic.TextileCraftDetector;
import nu.marginalia.wmsa.edge.converting.processor.logic.topic.WoodworkingDetector;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

public class ConverterLogicTestTool {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    DomPruner domPruner = new DomPruner();
    RecipeDetector recipeDetector = new RecipeDetector();
    WoodworkingDetector woodworkingDetector = new WoodworkingDetector();
    TextileCraftDetector textileCraftDetector = new TextileCraftDetector();

    SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());

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
        var cp = new ForkJoinPool(16);

        plan.forEachCrawledDomain(domain -> {
            if (domain.doc == null) return;


            for (var doc : domain.doc) {
                if (doc.documentBody == null) continue;

                Runnable task = () -> {
                    var parsed = Jsoup.parse(doc.documentBody);

                    domPruner.prune(parsed, 0.5);
                    var dld = se.extractSentences(parsed);

                    if (dld.totalNumWords() < 250)
                        return;

                    if (textileCraftDetector.testP(dld) > 0.3) {
                        System.out.println("textilecraft\t" + doc.url);
                    }
                    if (woodworkingDetector.testP(dld) > 0.2) {
                        System.out.println("woodworking\t" + doc.url);
                    }
                    if (recipeDetector.testP(dld) > 0.5) {
                        System.out.println("recipe\t" + doc.url);
                    }
                };

                if (cp.getQueuedSubmissionCount() > 32) {
                    task.run();
                } else {
                    cp.execute(task);
                }
            }
        });
    }

}
