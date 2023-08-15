package nu.marginalia.actor.state;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotation for declaring a state in an actor's state graph. */
@Retention(RetentionPolicy.RUNTIME)
public @interface ActorState {
    String name();
    String next() default "ERROR";
    String[] transitions() default {};
    String description() default "";
    ActorResumeBehavior resume() default ActorResumeBehavior.ERROR;
}
