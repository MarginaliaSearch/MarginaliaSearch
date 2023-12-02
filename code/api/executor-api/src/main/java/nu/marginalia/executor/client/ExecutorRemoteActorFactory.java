package nu.marginalia.executor.client;

import com.google.inject.Inject;
import jakarta.inject.Singleton;
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
        boolean trigger(T object) throws Exception;
        String getState();
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

    public boolean trigger(T object) throws Exception {
        return trigger(gson.toJson(object));
    }

    public boolean trigger(String payload) throws Exception {
        long id = persistence.sendNewMessage(inboxName, null, null, triggerFunction, payload, null);

        // Wait for the remote actor to respond to the message

        for (int i = 0; i < 120; i++) {
            var msg = persistence.getMessage(id);
            if (msg.state() == MqMessageState.ACK || msg.state() == MqMessageState.OK)
                return true;
            if (msg.state() == MqMessageState.ERR || msg.state() == MqMessageState.DEAD)
                return false;

            TimeUnit.SECONDS.sleep(1);
        }

        return false; // Timeout
    }

    public String getState() {
        return persistence
                .getHeadMessage(inboxName)
                .map(MqMessage::function)
                .orElse("INITIAL");
    }
}