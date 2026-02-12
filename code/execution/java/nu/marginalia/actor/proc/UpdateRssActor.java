package nu.marginalia.actor.proc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.rss.svc.FeedFetcherService;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class UpdateRssActor extends RecordActorPrototype {
    private final ActorTimeslot.ActorSchedule schedule = ActorTimeslot.RSS_FEEDS_SLOT;

    private final FeedFetcherService feedFetcherService;

    private final Duration updateInterval = Duration.ofHours(24);
    private final int cleanInterval = 60;
    private final int nodeId;

    private final NodeConfigurationService nodeConfigurationService;
    private static final Logger logger = LoggerFactory.getLogger(UpdateRssActor.class);

    @Inject
    public UpdateRssActor(Gson gson,
                          ServiceConfiguration serviceConfiguration, FeedFetcherService feedFetcherService,
                          NodeConfigurationService nodeConfigurationService) {
        super(gson);
        this.feedFetcherService = feedFetcherService;
        this.nodeId = serviceConfiguration.node();
        this.nodeConfigurationService = nodeConfigurationService;
    }

    public record Initial() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Run(int refreshCount) implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Wait(String startTs, String endTs, int count) implements ActorStep {

        public Wait(ActorTimeslot timeslot, int count) {
            this(timeslot.start().toString(), timeslot.end().toString(), count);
        }

    }

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record ScheduleNext(int count) implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Update(FeedFetcherService.UpdateMode mode, int refreshCount) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                if (nodeConfigurationService.get(nodeId).profile() != NodeProfile.REALTIME) {
                    yield new Error("Invalid node profile for RSS update");
                }
                else {
                    yield new ScheduleNext(0);
                }
            }

            case ScheduleNext(int refreshCount) ->
                    new Wait(schedule.nextTimeslot(), refreshCount);

            case Wait(String startTs, String endTs, int count) -> {
                var start = Instant.parse(startTs);
                var end = Instant.parse(endTs);
                var now = Instant.now();

                Thread.sleep(Duration.between(now, start));

                // Once every `cleanInterval` updates, do a clean update;
                // otherwise do a refresh update
                if (count > cleanInterval) {
                    yield new Update(FeedFetcherService.UpdateMode.CLEAN, 0);
                }
                else {
                    yield new Update(FeedFetcherService.UpdateMode.REFRESH, count);
                }
            }
            case Update(FeedFetcherService.UpdateMode mode, int count) -> {
                feedFetcherService.start(mode);
                yield new Run(count);
            }
            case Run(int refreshCount) -> {
                while (feedFetcherService.isRunning()) {
                    TimeUnit.SECONDS.sleep(15);
                }

                yield new ScheduleNext(refreshCount + 1);
            }
            case End() -> {
                if (feedFetcherService.isRunning()) {
                    feedFetcherService.stop();
                }
                yield new End(); // will not loop, terminal state
            }
            default -> new Error("Unknown actor step: " + self);
        };
    }

    @Override
    public String describe() {
        return "Periodically updates RSS and Atom feeds";
    }
}
