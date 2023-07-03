package nu.marginalia.mq;

public record MqMessage(
        long msgId,
        long relatedId,
        String function,
        String payload,
        MqMessageState state
) {
}
