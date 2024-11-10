package nu.marginalia.mq.task;

public interface MqTaskFunction {
    MqTaskResult run() throws Exception;
}
