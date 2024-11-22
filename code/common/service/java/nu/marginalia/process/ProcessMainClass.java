package nu.marginalia.process;

import com.google.gson.Gson;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.service.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class ProcessMainClass {
    private static final Logger logger = LoggerFactory.getLogger(ProcessMainClass.class);

    private final MessageQueueFactory messageQueueFactory;
    private final int node;
    private final String inboxName;

    static {
        // Load global config ASAP
        ConfigLoader.loadConfig(
                ConfigLoader.getConfigPath("system")
        );
    }

    private final Gson gson;

    public ProcessMainClass(MessageQueueFactory messageQueueFactory,
                            ProcessConfiguration config,
                            Gson gson,
                            String inboxName
                            ) {
        this.gson = gson;
        new org.mariadb.jdbc.Driver();
        this.messageQueueFactory = messageQueueFactory;
        this.node = config.node();
        this.inboxName = inboxName;
    }


    protected <T> Instructions<T> fetchInstructions(Class<T> requestType) throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(inboxName, node, UUID.randomUUID());

        logger.info("Waiting for instructions");

        var msgOpt = getMessage(inbox, requestType.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        // for live crawl, request is empty for now
        T request = gson.fromJson(msg.payload(), requestType);

        return new Instructions<>(msg, inbox, request);
    }


    private Optional<MqMessage> getMessage(MqSingleShotInbox inbox, String expectedFunction) throws InterruptedException, SQLException {
        var opt = inbox.waitForMessage(30, TimeUnit.SECONDS);
        if (opt.isPresent()) {
            if (!opt.get().function().equals(expectedFunction)) {
                throw new RuntimeException("Unexpected function: " + opt.get().function());
            }
            return opt;
        }
        else {
            var stolenMessage = inbox.stealMessage(msg -> msg.function().equals(expectedFunction));
            stolenMessage.ifPresent(mqMessage -> logger.info("Stole message {}", mqMessage));
            return stolenMessage;
        }
    }


    protected static class Instructions<T> {
        private final MqMessage message;
        private final MqSingleShotInbox inbox;
        private final T value;
        Instructions(MqMessage message, MqSingleShotInbox inbox, T value)
        {
            this.message = message;
            this.inbox = inbox;
            this.value = value;
        }

        public T value() {
            return value;
        }

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }

    }

}
