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
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleService;

import java.time.Duration;
import java.time.Instant;

@Singleton
public class DomSampleActor extends RecordActorPrototype {

    private final DomSampleService domSampleService;
    private final ActorScheduleService scheduleService;

    public record Initial() implements ActorStep {}

    @Resume(behavior= ActorResumeBehavior.RETRY)
    public record Wait(String startTs, String endTs) implements ActorStep {
        public Wait(ActorTimeslot timeslot) {
            this(timeslot.start().toString(), timeslot.end().toString());
        }
    }
    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Run(String endTs) implements ActorStep {}
    @Resume(behavior= ActorResumeBehavior.RETRY)
    public record Terminate() implements ActorStep {}
    private ActorTimeslot nextTimeslot() {
        return new ActorTimeslot.ActorSchedule(scheduleService.getWindow(ActorScheduleRow.Window.DOM_SAMPLE)).nextTimeslot();
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                yield new Wait(nextTimeslot());
            }

            case Wait(String startTs, String endTs) -> {
                var start = Instant.parse(startTs);
                var end = Instant.parse(endTs);
                var now = Instant.now();

                Thread.sleep(Duration.between(now, start));

                yield new Run(endTs);
            }

            case Run(String endTs) -> {
                Instant endTime = Instant.parse(endTs);
                if (endTime.isBefore(Instant.now()))
                    yield new Wait(nextTimeslot());

                if (!domSampleService.isRunning())
                    domSampleService.start();

                try {
                    while (Instant.now().isBefore(endTime)) {
                        if (!domSampleService.isRunning()) {
                            yield new Wait(nextTimeslot());
                        }
                        Thread.sleep(Duration.ofSeconds(15));
                    }
                }
                finally {
                    yield new Terminate();
                }


            }
            case Terminate() -> {
                domSampleService.stop();
                domSampleService.kill();
                yield new Wait(nextTimeslot());
            }
            case End() -> {
                if (domSampleService.isRunning()) {
                    domSampleService.stop();
                    domSampleService.kill();
                }
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
    public DomSampleActor(Gson gson, DomSampleService domSampleService, ActorScheduleService scheduleService)
    {
        super(gson);
        this.domSampleService = domSampleService;
        this.scheduleService = scheduleService;
    }

}
