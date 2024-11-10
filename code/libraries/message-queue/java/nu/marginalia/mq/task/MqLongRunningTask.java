package nu.marginalia.mq.task;

import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** A long-running task that can be executed asynchronously
 * and report back to the message queue.
 * <p></p>
 * The idiomatic pattern is to create an outbox and send a message corresponding to the task,
 * and then pass the message id along with the request to trigger the task over gRPC.
 * <p></p>
 * The gRPC service will spin off a thread and return immediately, while the task is executed
 * in the background.  The task can then report back to the message queue with the result
 * of the task as it completes.
 * */
public abstract class MqLongRunningTask implements Runnable {

    /** Create a new task with the given message id, name, and persistence.  If the msgId is
     * not positive, a dummy implementation is provided that does not report to the message queue.
     */
    public static MqLongRunningTask of(long msgId, String name, MqPersistence persistence) {
        if (msgId <= 0) {
            return new MqLongRunningTaskDummyImpl(name);
        }
        else {
            return new MqLongRunningTaskImpl(persistence, msgId, name);
        }
    }

    /** Creates a thread that will execute the task.  The thread is not started automatically */
    public Thread asThread(MqTaskFunction r) {
        return Thread.ofPlatform().name(name()).start(() -> runNow(r));
    }

    /** Creates a future that will execute the task on the provided ExecutorService. */
    public  Future<Boolean> asFuture(ExecutorService executor, MqTaskFunction r) {
        return executor.submit(() -> runNow(r));
    }

    /** Execute the task synchronously and return true if the task was successful */
    public boolean runNow(MqTaskFunction r) {
        try {
            switch (r.run()) {
                case MqTaskResult.Success success -> {
                    finish(success.message());
                    return true;
                }
                case MqTaskResult.Failure failure -> fail(failure.message());
            }
        }
        catch (Exception e) {
            fail(e);
        }
        return false;
    }

    abstract void finish();
    abstract void finish(String message);

    abstract void fail(String message);
    abstract void fail(Throwable e);

    public abstract String name();
}

class MqLongRunningTaskDummyImpl extends MqLongRunningTask {
    private final String name;

    MqLongRunningTaskDummyImpl(String name) {
        this.name = name;
    }

    @Override
    public void finish() {}

    @Override
    public void finish(String message) {}

    @Override
    public void fail(String message) {}

    @Override
    public void fail(Throwable e) {}

    @Override
    public void run() {}

    @Override
    public String name() {
        return name;
    }
}

class MqLongRunningTaskImpl extends MqLongRunningTask {
    private final MqPersistence persistence;
    private final long msgId;
    private final String name;

    MqLongRunningTaskImpl(MqPersistence persistence, long msgId, String name) {
        this.persistence = persistence;
        this.msgId = msgId;
        this.name = name;

        try {
            persistence.updateMessageState(msgId, MqMessageState.ACK);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void finish() {
        try {
            persistence.sendResponse(msgId, MqMessageState.OK, "Success");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void finish(String message) {
        try {
            persistence.sendResponse(msgId, MqMessageState.OK, message);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fail(String message) {
        try {
            persistence.sendResponse(msgId, MqMessageState.ERR, message);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void fail(Throwable e) {
        try {
            persistence.sendResponse(msgId, MqMessageState.ERR, e.getMessage());
        }
        catch (Exception e2) {
            throw new RuntimeException(e2);
        }
    }

    @Override
    public void run() {}

    @Override
    public String name() {
        return name;
    }
}