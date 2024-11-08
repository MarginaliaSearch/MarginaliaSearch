package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.service.module.ServiceConfiguration;

import java.time.Duration;
import java.time.LocalDateTime;

public class UpdateRssActor extends RecordActorPrototype {

    private final FeedsClient feedsClient;
    private final int nodeId;

    private final Duration initialDelay = Duration.ofMinutes(5);
    private final Duration updateInterval = Duration.ofHours(35);

    @Inject
    public UpdateRssActor(Gson gson, FeedsClient feedsClient, ServiceConfiguration serviceConfiguration) {
        super(gson);
        this.feedsClient = feedsClient;
        this.nodeId = serviceConfiguration.node();
    }

    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Wait(String ts) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record Update() implements ActorStep {}


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
                    yield new Wait(LocalDateTime.now().plus(initialDelay).toString());
                }
            }
            case Wait(String untilTs) -> {
                var until = LocalDateTime.parse(untilTs);
                var now = LocalDateTime.now();

                long remaining = Duration.between(now, until).toMillis();

                if (remaining > 0) {
                    Thread.sleep(remaining);
                    yield new Wait(untilTs);
                }
                else {
                    yield new Update();
                }
            }
            case Update() -> {
                feedsClient.updateFeeds();
                yield new Wait(LocalDateTime.now().plus(updateInterval).toString());
            }
            default -> new Error("Unknown actor step: " + self);
        };
    }

    @Override
    public String describe() {
        return "Periodically updates RSS and Atom feeds";
    }
}
