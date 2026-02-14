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
import nu.marginalia.rss.svc.FeedFetcherService.UpdateMode;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

public class UpdateRssActor extends RecordActorPrototype {
    private static final Logger logger = LoggerFactory.getLogger(UpdateRssActor.class);

    private final NodeConfigurationService nodeConfigurationService;
    private final FeedFetcherService feedFetcherService;

    private final ActorTimeslot.ActorSchedule schedule = ActorTimeslot.RSS_FEEDS_SLOT;

    private final int nodeId;

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
    public record Run() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Wait(String startTs, String endTs) implements ActorStep {
        public Wait(ActorTimeslot timeslot) {
            this(timeslot.start().toString(), timeslot.end().toString());
        }
    }

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record ScheduleNext() implements ActorStep {}

    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Update(UpdateMode mode) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                if (nodeConfigurationService.get(nodeId).profile() != NodeProfile.REALTIME) {
                    yield new Error("Invalid node profile for RSS update");
                }
                else {
                    yield new Wait(schedule.nextTimeslot());
                }
            }

            case Wait(String startTs, String endTs) -> {
                var start = Instant.parse(startTs);
                var end = Instant.parse(endTs);
                var now = Instant.now();

                Thread.sleep(Duration.between(now, start));

                final UpdateMode updateMode =
                    switch (LocalDate.now().getDayOfMonth()) {
                        case 1, 14 -> UpdateMode.CLEAN;
                        default -> UpdateMode.REFRESH;
                    };

                yield new Update(updateMode);
            }
            case Update(UpdateMode mode) -> {
                feedFetcherService.start(mode);
                yield new Run();
            }
            case Run() -> {
                while (feedFetcherService.isRunning()) {
                    TimeUnit.SECONDS.sleep(15);
                }

                yield new Wait(schedule.nextTimeslot());
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
