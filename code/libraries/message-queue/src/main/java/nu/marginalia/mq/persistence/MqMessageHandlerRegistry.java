package nu.marginalia.mq.persistence;

import java.util.concurrent.ConcurrentHashMap;

/** Keeps track of which thread is handling a message, to be able to
 * paint outgoing messages with a AUDIT_RELATED_ID to relate the
 * outgoing message to the incoming message that triggered it.
 * <p></p>
 * This is a pure audit field, a weaker version of the RELATED_ID,
 * which is used by e.g. state machines to relate a series of messages to each other.
 * <p></p>
 * The class is thread-safe, and tracks the thread ID of the thread that
 * is currently handling a message.  It can be cleaned up by calling
 * deregister() when the message has been handled.
 */
public class MqMessageHandlerRegistry {
    // There is some small risk of a memory leak here, if the registry entries aren't cleaned up properly,
    // but due to the low volume of messages being sent, this is not a big concern.  Since the average
    // message rate is less than 1 per second, even if the process ran for 60 years, and we leaked every ID
    // put in, the total amount of memory leaked would only be about of order 2 MB.

    private static final ConcurrentHashMap<Long, Long> handlerRegistry = new ConcurrentHashMap<>();

    public static void register(long msgId) {
        handlerRegistry.put(Thread.currentThread().threadId(), msgId);
    }

    public static long getOriginMessage() {
        return handlerRegistry.getOrDefault(Thread.currentThread().threadId(), -1L);
    }

    public static void deregister() {
        handlerRegistry.remove(Thread.currentThread().threadId());
    }
}
