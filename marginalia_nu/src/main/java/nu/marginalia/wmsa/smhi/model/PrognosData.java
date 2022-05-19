package nu.marginalia.wmsa.smhi.model;

import nu.marginalia.wmsa.smhi.model.dyn.Dygnsdata;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PrognosData {

    public String crawlTime = LocalDateTime.now().toString();

    public String approvedTime;
    public String referenceTime;
    public String expires;

    public Plats plats;

    public List<Tidpunkt> timeSeries = new ArrayList<>();

    public String getBastFore() {
        return LocalDateTime.parse(crawlTime).atZone(ZoneId.of("Europe/Stockholm"))
                .plusHours(3)
                .format(DateTimeFormatter.ISO_TIME);
    }
    public Plats getPlats() {
        return plats;
    }

    public List<Tidpunkt> getTidpunkter() {
        return timeSeries;
    }
    public List<Dygnsdata> getDygn() {
        return timeSeries.stream().map(Tidpunkt::getDate).distinct()
                .map(datum -> new Dygnsdata(datum, this))
                .collect(Collectors.toList());
    }
}
