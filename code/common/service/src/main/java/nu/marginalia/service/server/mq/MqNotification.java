package nu.marginalia.service.server.mq;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MqNotification {
    String endpoint();
}
