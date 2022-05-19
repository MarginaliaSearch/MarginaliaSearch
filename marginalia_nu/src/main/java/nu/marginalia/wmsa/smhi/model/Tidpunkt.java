package nu.marginalia.wmsa.smhi.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

public class Tidpunkt {

    private static final ZoneId serverZoneId = ZoneId.of("GMT");
    private static final ZoneId localZoneId = ZoneId.of("Europe/Stockholm");
    private static DateTimeFormatter timeFormatter = (new DateTimeFormatterBuilder())
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter();

    public String validTime;

    public List<Parameter> parameters = new ArrayList<>();


    private String getParam(String name) {
        var data = parameters.stream().filter(p -> name.equals(p.name)).map(p->p.values).findFirst().orElseGet(() -> new String[0]);
        if (data.length > 0) {
            return data[0];
        }
        return null;
    }
    public String getDate() {
        return ZonedDateTime.parse(validTime).toLocalDateTime().atZone(serverZoneId).toOffsetDateTime().atZoneSameInstant(localZoneId).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public String getTime() {
        return ZonedDateTime.parse(validTime).toLocalDateTime().atZone(serverZoneId).toOffsetDateTime().atZoneSameInstant(localZoneId).format(timeFormatter);
    }

    public String getTemp() {
        return getParam("t");
    }
    public String getMoln() {
        return getParam("tcc_mean");
    }
    public String getVind() {
        return getParam("ws");
    }
    public String getByvind() {
        return getParam("gust");
    }
    public String getNederbord() {
        return getParam("pmedian");
    }
    public String getNederbordTyp() {
        switch(getParam("pcat")) {
            case "1": return "S";
            case "2": return "SB";
            case "3": return "R";
            case "4": return "D";
            case "5": return "UKR";
            case "6": return "UKD";
            default:
                return "";

        }
    }
    public String getVindRiktning() {
        return getParam("wd");
    }
    public String toString() {
        return String.format("Tidpunkt[%s %s]", validTime, getTemp());
    }
}
