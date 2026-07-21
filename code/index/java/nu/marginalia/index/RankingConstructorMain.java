package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.index.api.IndexMqEndpoints;
import nu.marginalia.index.searchset.construction.RankingsCalculator;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.ranking.CreateRankingsRequest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RankingConstructorMain extends ProcessMainClass {
    private final ProcessHeartbeatImpl heartbeat;
    private final RankingsCalculator rankingsCalculator;
    private final MessageQueueFactory messageQueueFactory;
    private final int node;

    private static final Logger logger = LoggerFactory.getLogger(RankingConstructorMain.class);

    static void main(String[] args) throws Exception {
        Instructions<CreateRankingsRequest> instructions = null;
        try {
            new org.mariadb.jdbc.Driver();

            var main = Guice.createInjector(
                            new ProcessConfigurationModule("ranking-constructor"),
                            new ServiceDiscoveryModule(),
                            new DatabaseModule(false))
                    .getInstance(RankingConstructorMain.class);

            instructions = main.fetchInstructions(CreateRankingsRequest.class);
            main.run(instructions.value());
            instructions.ok();
        }
        catch (Exception ex) {
            logger.error("Ranking constructor failed", ex);

            if (instructions != null) {
                instructions.err();
            }
        }

        // Grace period so we don't rug pull the logger or jdbc
        TimeUnit.SECONDS.sleep(5);

        System.exit(0);
    }

    @Inject
    public RankingConstructorMain(MessageQueueFactory messageQueueFactory,
                                  ProcessConfiguration processConfiguration,
                                  ProcessHeartbeatImpl heartbeat,
                                  RankingsCalculator rankingsCalculator) {

        super(messageQueueFactory, processConfiguration, GsonFactory.get(), ProcessInboxNames.RANKING_CONSTRUCTOR_INBOX);

        this.heartbeat = heartbeat;
        this.rankingsCalculator = rankingsCalculator;
        this.messageQueueFactory = messageQueueFactory;
        this.node = processConfiguration.node();
    }

    private void run(CreateRankingsRequest instructions) throws Exception {
        heartbeat.start();

        switch (instructions.rankingsName()) {
            case PRIMARY -> rankingsCalculator.calculatePrimary();
            case SECONDARY -> rankingsCalculator.calculateSecondary();
        }

        // Nudge the index service on this node to pick up the new rankings from disk
        messageQueueFactory.sendSingleShotRequest(
                ServiceId.Index.withNode(node),
                IndexMqEndpoints.INDEX_RELOAD_SEARCH_SETS,
                null);

        heartbeat.shutDown();
    }

}
