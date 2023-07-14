package nu.marginalia.tools;

import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.tools.experiments.*;
import plan.CrawlPlanLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ExperimentRunnerMain {

    private static Map<String, Class<? extends Experiment>> experiments = Map.of(
            "test", TestExperiment.class,
            "adblock", AdblockExperiment.class,
            "topic", TopicExperiment.class,
            "sentence-statistics", SentenceStatisticsExperiment.class,
            "site-statistics", SiteStatisticsExperiment.class,
            "debug-converter", DebugConverterExperiment.class
    );

    public static void main(String... args) throws IOException {
        if (args.length < 2) {
            System.err.println("Expected arguments: plan.yaml experiment-name [experiment-args]");
            return;
        }

        if (!experiments.containsKey(args[1])) {
            System.err.println("Valid experiment names: " + experiments.keySet());
            return;
        }

        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new DatabaseModule(),
                new ConverterModule()
        );

        Experiment experiment = injector.getInstance(experiments.get(args[1]));

        experiment.args(Arrays.copyOfRange(args, 2, args.length));

        Map<String, String> idToDomain = new HashMap<>();
        for (var spec : plan.crawlingSpecificationIterable()) {
            idToDomain.put(spec.id, spec.domain);
        }

        for (var domain : plan.domainsIterable(id -> experiment.isInterested(idToDomain.get(id)))) {
            experiment.process(domain);
        }

        experiment.onFinish();

    }
}
