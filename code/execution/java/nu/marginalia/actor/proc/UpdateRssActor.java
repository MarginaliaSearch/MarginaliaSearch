package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.feeds.RpcFeedUpdateMode;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;

public class UpdateRssActor extends RecordActorPrototype {

    private final FeedsClient feedsClient;
    private final int nodeId;

    private final Duration initialDelay = Duration.ofMinutes(5);
    private final Duration updateInterval = Duration.ofHours(24);
    private final int cleanInterval = 60;

    private final NodeConfigurationService nodeConfigurationService;
    private final MqPersistence persistence;
    private static final Logger logger = LoggerFactory.getLogger(UpdateRssActor.class);

    @Inject
    public UpdateRssActor(Gson gson,
                          FeedsClient feedsClient,
                          ServiceConfiguration serviceConfiguration,
                          NodeConfigurationService nodeConfigurationService,
                          MqPersistence persistence) {
        super(gson);
        this.feedsClient = feedsClient;
        this.nodeId = serviceConfiguration.node();
        this.nodeConfigurationService = nodeConfigurationService;
        this.persistence = persistence;
    }

    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Wait(String ts, int refreshCount) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record UpdateRefresh(int refreshCount, long msgId) implements ActorStep {
        public UpdateRefresh(int refreshCount) {
            this(refreshCount, -1);
        }
    }
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record UpdateClean(long msgId) implements ActorStep {
        public UpdateClean() {
            this(-1);
        }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                if (nodeConfigurationService.get(nodeId).profile() != NodeProfile.REALTIME) {
                    yield new Error("Invalid node profile for RSS update");
                }
                else {
                    // Wait for 5 minutes before starting the first update, to give the system time to start up properly
                    yield new Wait(LocalDateTime.now().plus(initialDelay).toString(), 0);
                }
            }
            case Wait(String untilTs, int count) -> {
                var until = LocalDateTime.parse(untilTs);
                var now = LocalDateTime.now();

                long remaining = Duration.between(now, until).toMillis();

                if (remaining > 0) {
                    Thread.sleep(remaining);
                    yield new Wait(untilTs, count);
                }
                else {

                    // Once every `cleanInterval` updates, do a clean update;
                    // otherwise do a refresh update
                    if (count > cleanInterval) {
                        yield new UpdateClean();
                    }
                    else {
                        yield new UpdateRefresh(count);
                    }

                }
            }
            case UpdateRefresh(int count, long msgId) when msgId < 0 -> {
                long messageId = feedsClient.updateFeeds(RpcFeedUpdateMode.REFRESH);
                yield new UpdateRefresh(count, messageId);
            }
            case UpdateRefresh(int count, long msgId) -> {
                MqMessage msg = persistence.waitForMessageTerminalState(msgId, Duration.ofSeconds(10), Duration.ofHours(12));
                if (msg == null) {
                    logger.warn("UpdateRefresh is taking a very long time");
                    yield new UpdateRefresh(count, msgId);
                } else if (msg.state() != MqMessageState.OK) {
                    // Retry the update
                    yield new Error("Failed to update feeds: " + msg.state());
                }
                else {
                    // Increment the refresh count
                    yield new Wait(LocalDateTime.now().plus(updateInterval).toString(), count + 1);
                }
            }
            case UpdateClean(long msgId) when msgId < 0 -> {
                long messageId = feedsClient.updateFeeds(RpcFeedUpdateMode.CLEAN);
                yield new UpdateClean(messageId);
            }
            case UpdateClean(long msgId) -> {
                MqMessage msg = persistence.waitForMessageTerminalState(msgId, Duration.ofSeconds(10), Duration.ofHours(12));
                if (msg == null) {
                    logger.warn("UpdateClean is taking a very long time");
                    yield new UpdateClean(msgId);
                } else if (msg.state() != MqMessageState.OK) {
                    // Retry the update
                    yield new Error("Failed to update feeds: " + msg.state());
                }
                else {
                    // Reset the refresh count after a successful update
                    yield new Wait(LocalDateTime.now().plus(updateInterval).toString(), 0);
                }
            }
            default -> new Error("Unknown actor step: " + self);
        };
    }

    @Override
    public String describe() {
        return "Periodically updates RSS and Atom feeds";
    }
}
