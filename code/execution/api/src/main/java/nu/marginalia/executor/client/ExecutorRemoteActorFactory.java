package nu.marginalia.executor.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.model.gson.GsonFactory;
import com.google.gson.Gson;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.storage.model.FileStorageId;

import java.util.concurrent.TimeUnit;

/** This class permits direct interaction with executor actors.  This is inherently a pretty flimsy abstraction,
 * and care must be taken to not leak too many implementation details through this interface.
 */
@Singleton
public class ExecutorRemoteActorFactory {
    private final MqPersistence persistence;

    @Inject
    public ExecutorRemoteActorFactory(MqPersistence persistence) {
        this.persistence = persistence;
    }

    /** Create a remote actor for the RecrawlActor */
    public ExecutorRemoteActorIf<CrawlData> createCrawlRemote(int node) {
        return new ExecutorRemoteActor<>(persistence, "fsm:recrawl:" + node, "INITIAL");
    }

    /** Create a remote actor for the ConvertAndLoadActor */
    public ExecutorRemoteActorIf<ConvertAndLoadData> createConvertAndLoadRemote(int node) {
        return new ExecutorRemoteActor<>(persistence, "fsm:convert_and_load:" + node, "INITIAL");
    }

    public interface ExecutorRemoteActorIf<T> {

        /** Trigger the remote actor with the given object.  The object will be serialized to JSON and sent to the
         * remote actor.  If the remote actor does not respond after a time period, a timeout will occur and a negative
         * message id will be returned.
         *
         * @param object The message to send to the remote actot
         * @return The message id of the response message, or a negative number if the remote actor did not respond
         * within a reasonable timeout seconds.
         */
        long trigger(T object) throws Exception;

        /** Get the last finished state of the actor.
         * <p>
         * The message id of the request initiating the actor must be provided to ensure that
         * we don't get a state from a previous run.
         */
        String getState(long fromMsgId);
    }

    public record CrawlData(FileStorageId storageId, boolean cascadeLoad) {}
    public record ConvertAndLoadData(FileStorageId fid) {}
}


class ExecutorRemoteActor<T> implements ExecutorRemoteActorFactory.ExecutorRemoteActorIf<T> {
    private final MqPersistence persistence;
    private final String inboxName;
    private final String triggerFunction;
    private static final Gson gson = GsonFactory.get();

    ExecutorRemoteActor(MqPersistence persistence,
                        String inboxName,
                        String triggerFunction
                        ) {
        this.persistence = persistence;
        this.inboxName = inboxName;
        this.triggerFunction = triggerFunction;
    }

    public long trigger(T object) throws Exception {
        return trigger(gson.toJson(object));
    }

    public long trigger(String payload) throws Exception {
        long id = persistence.sendNewMessage(inboxName, null, null, triggerFunction, payload, null);

        // Wait for the remote actor to respond to the message

        for (int i = 0; i < 120; i++) {
            var msg = persistence.getMessage(id);
            if (msg.state() == MqMessageState.ACK || msg.state() == MqMessageState.OK)
                return id;
            if (msg.state() == MqMessageState.ERR || msg.state() == MqMessageState.DEAD)
                return -id;

            TimeUnit.SECONDS.sleep(1);
        }

        return -1; // Timeout
    }

    public String getState(long fromMsgId) {
        return persistence
                .getHeadMessage(inboxName, fromMsgId)
                .map(MqMessage::function)
                .orElse("INITIAL");
    }
}