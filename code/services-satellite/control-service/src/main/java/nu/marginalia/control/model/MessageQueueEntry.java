package nu.marginalia.control.model;

public record MessageQueueEntry (
        long id,
        long relatedId,
        String senderInbox,
        String recipientInbox,
        String function,
        String ownerInstanceFull,
        long ownerTick,
        String state,
        String createdTime,
        String updatedTime,
        int ttl
)
{
    public String ownerInstance() {
        if (ownerInstanceFull == null) {
            return "";
        }

        return ownerInstanceFull.substring(0, 8);
    }
    public String ownerInstanceColor() {
        if (ownerInstanceFull == null) {
            return "#000000";
        }
        return '#' + ownerInstanceFull.substring(0, 6);
    }
    public String ownerInstanceColor2() {
        if (ownerInstanceFull == null) {
            return "#000000";
        }

        return '#' + ownerInstanceFull.substring(25, 31);
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
}
