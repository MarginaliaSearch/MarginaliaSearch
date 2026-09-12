package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleRow.*;
import nu.marginalia.schedule.ActorScheduleService;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleService {
    private final ControlRendererFactory rendererFactory;
    private final ActorScheduleService actorScheduleService;

    @Inject
    public ScheduleService(ControlRendererFactory rendererFactory,
                           ActorScheduleService actorScheduleService) {
        this.rendererFactory = rendererFactory;
        this.actorScheduleService = actorScheduleService;
    }

    public void register(Jooby jooby) throws IOException {
        var schedulesRenderer = rendererFactory.renderer("control/sys/schedules");

        jooby.get("/schedules", ctx -> schedulesRenderer.render(schedulesModel(ctx)));
        jooby.post("/schedules", ctx -> Redirects.redirectToSchedules.render(updateSchedule(ctx)));
    }

    private Object schedulesModel(Context ctx) {
        List<WindowSchedule> windowSchedules = new ArrayList<>();
        List<TriggerSchedule> triggerSchedules = new ArrayList<>();
        List<IntervalSchedule> intervalSchedules = new ArrayList<>();

        for (ActorScheduleRow row : actorScheduleService.getAllSchedules()) {
            switch (row) {
                case WindowSchedule w -> windowSchedules.add(w);
                case TriggerSchedule t -> triggerSchedules.add(t);
                case IntervalSchedule i -> intervalSchedules.add(i);
            }
        }

        return Map.of(
                "windowSchedules", windowSchedules,
                "triggerSchedules", triggerSchedules,
                "intervalSchedules", intervalSchedules
        );
    }

    private Object updateSchedule(Context ctx) throws SQLException {
        String scheduleName = ctx.lookup("scheduleName", QUERY, FORM).valueOrNull();
        String type = ctx.lookup("type", QUERY, FORM).valueOrNull();

        switch (type) {
            case "window" -> {
                int startHour = Integer.parseInt(ctx.lookup("startHour", QUERY, FORM).valueOrNull());
                int endHour = Integer.parseInt(ctx.lookup("endHour", QUERY, FORM).valueOrNull());
                actorScheduleService.updateWindow(ActorScheduleRow.Window.valueOf(scheduleName), startHour, endHour);
            }
            case "trigger" -> {
                int triggerHour = Integer.parseInt(ctx.lookup("triggerHour", QUERY, FORM).valueOrNull());
                actorScheduleService.updateTrigger(ActorScheduleRow.Trigger.valueOf(scheduleName), triggerHour);
            }
            case "interval" -> {
                int intervalHours = Integer.parseInt(ctx.lookup("intervalHours", QUERY, FORM).valueOrNull());
                actorScheduleService.updateInterval(ActorScheduleRow.Interval.valueOf(scheduleName), intervalHours);
            }
            default -> throw new IllegalArgumentException("Unknown schedule type: " + type);
        }

        return "";
    }
}
