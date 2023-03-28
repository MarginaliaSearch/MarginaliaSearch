package nu.marginalia.tools;

import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.tools.experiments.*;
import plan.CrawlPlanLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class ExperimentRunnerMain {

    private static Map<String, Class<? extends Experiment>> experiments = Map.of(
            "test", TestExperiment.class,
            "adblock", AdblockExperiment.class,
            "topic", TopicExperiment.class,
            "statistics", SentenceStatisticsExperiment.class
    );

    public static void main(String... args) throws IOException {
        if (args.length != 2) {
            System.err.println("Expected arguments: plan.yaml experiment-name");
            return;
        }

        if (!experiments.containsKey(args[1])) {
            System.err.println("Valid experiment names: " + experiments.keySet());
            return;
        }

        Injector injector = Guice.createInjector(
                new DatabaseModule()
        );

        Experiment experiment = injector.getInstance(experiments.get(args[1]));

        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        for (var domain : plan.domainsIterable()) { // leaks file descriptor, is fine
            if (!experiment.process(domain)) {
                break;
            }
        }
        experiment.onFinish();

    }
}
