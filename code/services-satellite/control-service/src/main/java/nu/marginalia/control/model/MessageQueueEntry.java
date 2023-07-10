package nu.marginalia.control.model;

public record MessageQueueEntry (
        long id,
        long relatedId,
        String senderInbox,
        String recipientInbox,
        String function,
        String ownerInstance,
        long ownerTick,
        String state,
        String createdTime,
        String updatedTime,
        int ttl
)
{
}
