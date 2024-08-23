package nu.marginalia.service.server.mq;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ServiceMqSubscription implements MqSubscription {
    private static final Logger logger = LoggerFactory.getLogger(ServiceMqSubscription.class);
    private final Map<String, Method> requests = new HashMap<>();
    private final Object service;


    public ServiceMqSubscription(Object service) {
        this.service = service;

        /* Wire up all methods annotated with @MqRequest and @MqNotification
         * to receive corresponding messages from this subscription */
        for (var method : service.getClass().getMethods()) {
            var annotation = method.getAnnotation(MqRequest.class);
            if (annotation != null) {
                requests.put(annotation.endpoint(), method);
            }
        }
    }

    @Override
    public boolean filter(MqMessage rawMessage) {
        if (requests.containsKey(rawMessage.function())) {
            return true;
        }

        logger.warn("Received message for unknown function " + rawMessage.function());

        return false;
    }

    @Override
    public MqInboxResponse onRequest(MqMessage msg) {
        var method = requests.get(msg.function());

        if (null == method) {
            logger.error("Received message for unregistered function handler " + msg.function());
            return MqInboxResponse.err("[No handler]");
        }

        try {
            if (method.getReturnType() == void.class) {
                method.invoke(service, msg.payload());
                return MqInboxResponse.ok();
            }
            else {
                // Do we want to just toString() here?  Gson? Something else?
                String rv = method.invoke(service, msg.payload()).toString();
                return MqInboxResponse.ok(rv);
            }
        }
        catch (InvocationTargetException ex) {
            logger.error("Error invoking method " + method, ex);
            return MqInboxResponse.err(ex.getCause().getMessage());
        }
        catch (Exception ex) {
            logger.error("Error invoking method " + method, ex);
            return MqInboxResponse.err(ex.getMessage());
        }
    }

}
