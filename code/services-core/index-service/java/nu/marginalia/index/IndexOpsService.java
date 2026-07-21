package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.task.ActorProcessWatcher;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.connectivity.ConnectivitySets;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.ranking.CreateRankingsRequest;
import nu.marginalia.mqapi.ranking.RankingsName;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;

import javax.annotation.CheckReturnValue;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class IndexOpsService {
    private final ReentrantLock opsLock = new ReentrantLock();

    private final StatefulIndex index;
    private final SearchSetsService searchSetService;
    private final ConnectivitySets connectivitySets;
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox rankingConstructorOutbox;

    @Inject
    public IndexOpsService(StatefulIndex index,
                           SearchSetsService searchSetService,
                           ConnectivitySets connectivitySets,
                           ActorProcessWatcher processWatcher,
                           ProcessOutboxes processOutboxes) {
        this.index = index;
        this.searchSetService = searchSetService;
        this.connectivitySets = connectivitySets;
        this.processWatcher = processWatcher;
        this.rankingConstructorOutbox = processOutboxes.getRankingConstructorOutbox();
    }

    public boolean isBusy() {
        return opsLock.isLocked();
    }

    public boolean rerank() throws Exception {
        return run(() -> {
            constructRankings(RankingsName.PRIMARY);

            // The primary rank calculation also refreshes the connectivity map,
            // which the index service holds in memory
            connectivitySets.reload();

            return true;
        }).isPresent();
    }

    public boolean repartition() throws Exception {
        return run(() -> {
            constructRankings(RankingsName.SECONDARY);

            searchSetService.reload();

            return true;
        }).isPresent();
    }

    private void constructRankings(RankingsName rankingsName) throws Exception {
        long msgId = rankingConstructorOutbox.sendAsync(new CreateRankingsRequest(rankingsName));

        var rsp = processWatcher.waitResponse(rankingConstructorOutbox,
                ProcessSpawnerService.ProcessId.RANKING_CONSTRUCTOR,
                msgId);

        if (rsp.state() != MqMessageState.OK) {
            throw new IllegalStateException("Ranking constructor process failed with message state " + rsp.state());
        }
    }

    /** @return true if the index was switched
     *
     * @param additionalWork additional work to perform during the index switch operation
     * */
    public boolean switchIndex(StatefulIndex.SwitchoverTask additionalWork) throws Exception {
        return run(() -> index.switchIndex(additionalWork)).orElse(false);
    }


    @CheckReturnValue
    public <T> Optional<T> run(Callable<T> c) throws Exception {
        if (!opsLock.tryLock())
            return Optional.empty();

        try {
            return Optional.of(c.call());
        }
        finally {
            opsLock.unlock();
        }
    }

}
