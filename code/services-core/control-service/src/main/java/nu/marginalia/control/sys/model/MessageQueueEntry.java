package nu.marginalia.control.sys.model;

import java.time.LocalDate;

public record MessageQueueEntry (
        long id,
        long relatedId,
        String senderInbox,
        String recipientInbox,
        String function,
        String payload,
        String ownerInstanceFull,
        long ownerTick,
        String state,
        String createdTimeFull,
        String updatedTimeFull,
        int ttl
)
{
    public boolean hasRelatedMessage() {
        return relatedId > 0;
    }

    public String stateCode() {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case "NEW" -> "\uD83D\uDC23";
            case "ACK" -> "\uD83D\uDD27";
            case "ERR" -> "\u274C";
            case "OK" -> "\u2705";
            case "DEAD" -> "\uD83D\uDC80";
            default -> "";
        };
    }

    public String getCreatedTime() {
        String retDateBase = createdTimeFull.replace('T', ' ');

        // if another day, return date, hour and minute
        if (!createdTimeFull.startsWith(LocalDate.now().toString())) {
            // return hour minute and seconds
            return retDateBase.substring(0, "YYYY-MM-DDTHH:MM".length());
        }
        else { // return date, hour and minute but not seconds or ms
            return retDateBase.substring("YYYY-MM-DDT".length(), "YYYY-MM-DDTHH:MM:SS".length());
        }
    }

    public String getUpdatedTime() {
        String retDateBase = updatedTimeFull.replace('T', ' ');

        // if another day, return date, hour and minute
        if (!updatedTimeFull.startsWith(LocalDate.now().toString())) {
            // return hour minute and seconds
            return retDateBase.substring(0, "YYYY-MM-DDTHH:MM".length());
        }
        else { // return date, hour and minute but not seconds or ms
            return retDateBase.substring("YYYY-MM-DDT".length(), "YYYY-MM-DDTHH:MM:SS".length());
        }
    }
}
