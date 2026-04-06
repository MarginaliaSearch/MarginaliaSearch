package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.schedule.ActorScheduleRow;
import nu.marginalia.schedule.ActorScheduleRow.*;
import nu.marginalia.schedule.ActorScheduleService;
import spark.Request;
import spark.Response;
import spark.Spark;

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

    public void register() throws IOException {
        var schedulesRenderer = rendererFactory.renderer("control/sys/schedules");

        Spark.get("/schedules", this::schedulesModel, schedulesRenderer::render);
        Spark.post("/schedules", this::updateSchedule, Redirects.redirectToSchedules);
    }

    private Object schedulesModel(Request request, Response response) {
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

    private Object updateSchedule(Request request, Response response) throws SQLException {
        String scheduleName = request.queryParams("scheduleName");
        String type = request.queryParams("type");

        switch (type) {
            case "window" -> {
                int startHour = Integer.parseInt(request.queryParams("startHour"));
                int endHour = Integer.parseInt(request.queryParams("endHour"));
                actorScheduleService.updateWindow(ActorScheduleRow.Window.valueOf(scheduleName), startHour, endHour);
            }
            case "trigger" -> {
                int triggerHour = Integer.parseInt(request.queryParams("triggerHour"));
                actorScheduleService.updateTrigger(ActorScheduleRow.Trigger.valueOf(scheduleName), triggerHour);
            }
            case "interval" -> {
                int intervalHours = Integer.parseInt(request.queryParams("intervalHours"));
                actorScheduleService.updateInterval(ActorScheduleRow.Interval.valueOf(scheduleName), intervalHours);
            }
            default -> throw new IllegalArgumentException("Unknown schedule type: " + type);
        }

        return "";
    }
}
