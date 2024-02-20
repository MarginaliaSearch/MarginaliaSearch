package nu.marginalia.control.sys.model;

public record MessageQueueEntry (
        long id,
        long relatedId,
        long auditRelatedId,
        String senderInbox,
        String recipientInbox,
        String function,
        String payload,
        String ownerInstanceFull,
        long ownerTick,
        String state,
        String createdTime,
        String updatedTime,
        int ttl
)
{
    public boolean hasRelatedMessage() {
        return relatedId > 0;
    }
    public boolean hasAuditRelation() {
        return auditRelatedId > 0;
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
