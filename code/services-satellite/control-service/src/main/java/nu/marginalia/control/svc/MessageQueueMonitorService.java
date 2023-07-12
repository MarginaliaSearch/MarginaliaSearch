package nu.marginalia.control.svc;

import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.control.ServiceEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class MessageQueueMonitorService {
    private final Logger logger = LoggerFactory.getLogger(MessageQueueMonitorService.class);
    private final MqPersistence persistence;
    private final ServiceEventLog eventLog;

    @Inject
    public MessageQueueMonitorService(ServiceEventLog eventLog, MqPersistence persistence) {
        this.eventLog = eventLog;
        this.persistence = persistence;

        Thread reaperThread = new Thread(this::run, "message-queue-reaper");
        reaperThread.setDaemon(true);
        reaperThread.start();
    }


    private void run() {

        for (;;) {
            try {
                TimeUnit.MINUTES.sleep(10);

                reapMessages();
            }
            catch (InterruptedException ex) {
                logger.info("Message queue reaper interrupted");
                break;
            }
            catch (Exception ex) {
                logger.error("Message queue reaper failed", ex);
            }
        }
    }

    private void reapMessages() throws SQLException {
        int outcome = persistence.reapDeadMessages();
        if (outcome > 0) {
            eventLog.logEvent("MESSAGE-QUEUE-REAPED", Integer.toString(outcome));
            logger.info("Reaped {} dead messages from message queue", outcome);
        }

        outcome = persistence.cleanOldMessages();
        if (outcome > 0) {
            eventLog.logEvent("MESSAGE-QUEUE-CLEANED", Integer.toString(outcome));
            logger.info("Cleaned {} stale messages from message queue", outcome);
        }
    }

}
