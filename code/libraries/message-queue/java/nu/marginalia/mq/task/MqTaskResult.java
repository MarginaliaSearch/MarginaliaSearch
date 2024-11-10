package nu.marginalia.mq.task;

public sealed interface MqTaskResult {
    record Success(String message) implements MqTaskResult {
        public Success(){
            this("Ok");
        }
    }
    record Failure(String message) implements MqTaskResult {
        public Failure(Throwable e){
            this(e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }
}
