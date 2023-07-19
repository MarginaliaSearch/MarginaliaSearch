package nu.marginalia.mqsm.graph;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface GraphState {
    String name();
    String next() default "ERROR";
    String[] transitions() default {};
    String description() default "";
    ResumeBehavior resume() default ResumeBehavior.ERROR;
}
