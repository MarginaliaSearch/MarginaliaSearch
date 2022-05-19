package nu.marginalia.wmsa.smhi.model.dyn;

import nu.marginalia.wmsa.smhi.model.PrognosData;
import nu.marginalia.wmsa.smhi.model.Tidpunkt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class Dygnsdata {
    public final String date;
    private final PrognosData data;

    public Dygnsdata(String date, PrognosData data) {
        this.date = date;
        this.data = data;
    }

    public String getDate() {
        return date;
    }
    public List<Tidpunkt> getData() {
        String d = getDate();
        return data.timeSeries.stream().filter(p -> d.equals(p.getDate())).collect(Collectors.toList());
    }

    public String getVeckodag() {
        switch (LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).getDayOfWeek()) {
            case MONDAY: return "M&aring;ndag";
            case TUESDAY: return "Tisdag";
            case WEDNESDAY: return "Onsdag";
            case THURSDAY: return "Torsdag";
            case FRIDAY: return "Fredag";
            case SATURDAY: return "L&ouml;rdag";
            case SUNDAY: return "S&ouml;ndag";
        }
        return "Annandag";
    }
}
