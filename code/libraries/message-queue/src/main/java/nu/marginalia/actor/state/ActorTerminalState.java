package nu.marginalia.actor.state;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ActorTerminalState {
    String name();
    String description() default "";
}
