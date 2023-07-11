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
        return ownerInstanceFull.substring(0, 8);
    }
    public String ownerInstanceColor() {
        return '#' + ownerInstanceFull.substring(0, 6);
    }
    public String ownerInstanceColor2() {
        return '#' + ownerInstanceFull.substring(25, 31);
    }
}
