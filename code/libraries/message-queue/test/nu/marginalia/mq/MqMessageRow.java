package nu.marginalia.mq;

import javax.annotation.Nullable;

public record MqMessageRow (
        long id,
        long relatedId,
        @Nullable
        String senderInbox,
        String recipientInbox,
        String function,
        String payload,
        MqMessageState state,
        String ownerInstance,
        long ownerTick,
        long createdTime,
        long updatedTime,
        long ttl
) {}