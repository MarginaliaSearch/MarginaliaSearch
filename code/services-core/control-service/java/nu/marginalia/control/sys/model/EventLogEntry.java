package nu.marginalia.control.sys.model;

import java.time.LocalDate;

public record EventLogEntry(
        long id,
        String serviceName,
        String instanceFull,
        String eventDateTime,
        String eventType,
        String eventMessage)
{
    public String getEventTime() {
        String retDateBase = eventDateTime.replace('T', ' ');

        // if another day, return date, hour and minute
        if (!eventDateTime.startsWith(LocalDate.now().toString())) {
            // return hour minute and seconds
            return retDateBase.substring(0, "YYYY-MM-DDTHH:MM".length());
        }
        else { // return date, hour and minute but not seconds or ms
            return retDateBase.substring("YYYY-MM-DDT".length(), "YYYY-MM-DDTHH:MM:SS".length());
        }
    }
}
