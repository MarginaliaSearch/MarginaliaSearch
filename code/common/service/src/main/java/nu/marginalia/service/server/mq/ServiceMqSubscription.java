package nu.marginalia.service.server.mq;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSubscription;
import nu.marginalia.service.server.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ServiceMqSubscription implements MqSubscription {
    private static final Logger logger = LoggerFactory.getLogger(ServiceMqSubscription.class);
    private final Map<String, Method> requests = new HashMap<>();
    private final Map<String, Method> notifications = new HashMap<>();
    private final Service service;

    public ServiceMqSubscription(Service service) {
        this.service = service;
        for (var method : service.getClass().getMethods()) {
            var annotation = method.getAnnotation(MqRequest.class);
            if (annotation != null) {
                requests.put(annotation.endpoint(), method);
            }
            if (method.getAnnotation(MqNotification.class) != null) {
                notifications.put(method.getName(), method);
            }
        }
    }

    @Override
    public boolean filter(MqMessage rawMessage) {
        boolean isInteresting = requests.containsKey(rawMessage.function())
                || notifications.containsKey(rawMessage.function());

        if (!isInteresting) {
            logger.warn("Received message for unknown function " + rawMessage.function());
        }

        return isInteresting;
    }

    @Override
    public MqInboxResponse onRequest(MqMessage msg) {
        var method = requests.get(msg.function());

        try {
            return MqInboxResponse.ok(method.invoke(service, msg.payload()).toString());
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

    @Override
    public void onNotification(MqMessage msg) {
        var method = notifications.get(msg.function());

        try {
            method.invoke(service, msg.payload());
        }
        catch (Exception ex) {
            logger.error("Error invoking method " + method, ex);
        }
    }
}
