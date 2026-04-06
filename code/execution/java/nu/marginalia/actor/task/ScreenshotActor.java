package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.livecapture.LiveCaptureGrpcService;
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleService;

import java.time.Duration;
import java.time.Instant;

@Singleton
public class ScreenshotActor extends RecordActorPrototype {

    private final LiveCaptureGrpcService liveCaptureService;
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

    private ActorTimeslot nextTimeslot() {
        return new ActorTimeslot.ActorSchedule(scheduleService.getWindow(ActorScheduleRow.Window.SCREENGRAB)).nextTimeslot();
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

                liveCaptureService.setAllowed(true);

                try {
                    while (Instant.now().isBefore(endTime)) {
                        if (!liveCaptureService.isAllowed()) {
                            yield new Wait(nextTimeslot());
                        }
                        Thread.sleep(Duration.ofSeconds(15));
                    }
                }
                finally {
                    liveCaptureService.setAllowed(false);
                }

                yield new Wait(nextTimeslot());
            }
            case End() -> {
                liveCaptureService.setAllowed(false);

                yield new End(); // will not run, terminal state
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Run screenshot capture service";
    }

    @Inject
    public ScreenshotActor(Gson gson, LiveCaptureGrpcService liveCaptureService, ActorScheduleService scheduleService)
    {
        super(gson);
        this.liveCaptureService = liveCaptureService;
        this.scheduleService = scheduleService;
    }

}
