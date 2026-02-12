package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.domsample.DomSampleService;

import java.time.Duration;
import java.time.Instant;

@Singleton
public class DomSampleActor extends RecordActorPrototype {
    private final DomSampleService domSampleService;

    private final int SCHEDULE_START_TIME_UTC = 16;
    private final int SCHEDULE_RUN_TIME_HOURS = 8;

    public record Initial() implements ActorStep {}
    @Resume(behavior= ActorResumeBehavior.RETRY)
    public record Wait(String untilTs) implements ActorStep {
        public Wait(Instant instant) {
            this(instant.toString());
        }
    }
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record ScheduleNext() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Run() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                yield new ScheduleNext();
            }
            case ScheduleNext() ->
                    new Wait(ActorTimeslot.dailyAtHourUTC(SCHEDULE_START_TIME_UTC));

            case Wait(String untilTs) -> {
                var until = Instant.parse(untilTs);
                var now = Instant.now();

                Thread.sleep(Duration.between(now, until));
                yield new Run();
            }

            case Run() -> {
                if (!domSampleService.isRunning())
                    domSampleService.start();

                Instant runStart = Instant.now();
                Instant runEnd = runStart.plus(Duration.ofHours(SCHEDULE_RUN_TIME_HOURS));

                try {
                    while (Instant.now().isBefore(runEnd)) {
                        if (!domSampleService.isRunning()) {
                            yield new ScheduleNext();
                        }
                        Thread.sleep(Duration.ofSeconds(15));
                    }
                }
                finally {
                    domSampleService.stop();
                }

                yield new ScheduleNext();
            }
            case End() -> {
                if (domSampleService.isRunning())
                    domSampleService.stop();
                yield new End(); // will not run, terminal state
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Run DOM sample service";
    }

    @Inject
    public DomSampleActor(Gson gson, DomSampleService domSampleService, ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.domSampleService = domSampleService;
    }

}
