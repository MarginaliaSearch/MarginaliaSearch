package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.feeds.RpcFeedUpdateMode;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.service.module.ServiceConfiguration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class UpdateRssActor extends RecordActorPrototype {

    private final FeedsClient feedsClient;
    private final int nodeId;

    private final MqOutbox updateTaskOutbox;

    private final Duration initialDelay = Duration.ofMinutes(5);
    private final Duration updateInterval = Duration.ofHours(24);
    private final int cleanInterval = 60;

    @Inject
    public UpdateRssActor(Gson gson, FeedsClient feedsClient, ServiceConfiguration serviceConfiguration) {
        super(gson);
        this.feedsClient = feedsClient;
        this.nodeId = serviceConfiguration.node();
        this.updateTaskOutbox = feedsClient.createOutbox("update-rss-actor", nodeId);
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
                if (nodeId > 1) {
                    // Only run on the first node
                    yield new End();
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
                long messageId =  updateTaskOutbox.sendAsync("UpdateRefresh", "");
                feedsClient.updateFeeds(RpcFeedUpdateMode.REFRESH, messageId);
                yield new UpdateRefresh(count, messageId);
            }
            case UpdateRefresh(int count, long msgId) -> {
                var rsp = updateTaskOutbox.waitResponse(msgId, 12, TimeUnit.HOURS);
                if (rsp.state() != MqMessageState.OK) {
                    // Retry the update
                    yield new Error("Failed to update feeds: " + rsp.state());
                }
                else {
                    // Reset the refresh count after a successful update
                    yield new Wait(LocalDateTime.now().plus(updateInterval).toString(), count + 1);
                }
            }
            case UpdateClean(long msgId) when msgId < 0 -> {
                long messageId =  updateTaskOutbox.sendAsync("UpdateClean", "");
                feedsClient.updateFeeds(RpcFeedUpdateMode.CLEAN, messageId);

                yield new UpdateClean(messageId);
            }
            case UpdateClean(long msgId) -> {
                var rsp = updateTaskOutbox.waitResponse(msgId, 12, TimeUnit.HOURS);
                if (rsp.state() != MqMessageState.OK) {
                    // Retry the update
                    yield new Error("Failed to clean feeds: " + rsp.state());
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
