package nu.marginalia.piping;

import nu.marginalia.buffering.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public abstract class AbstractPipeStage<T> implements PipeStage<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public final String stageName;

    private final RingBuffer<T> inputBuffer;
    private final AtomicInteger instanceCount = new AtomicInteger(0);
    private final AtomicInteger desiredInstanceCount = new AtomicInteger(0);
    private final ExecutorService executorService;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final AtomicReference<Thread> idleRunner = new AtomicReference<>(null);
    private final AtomicReference<Thread> idleSubmitter = new AtomicReference<>(null);
    private final AtomicInteger idleRunnerCount = new AtomicInteger();

    protected AbstractPipeStage(String stageName, int size, ExecutorService executorService) {
        this.stageName = stageName;
        this.inputBuffer = new RingBuffer<>(size);
        this.executorService = executorService;
    }

    @Override
    public abstract Optional<PipeStage<?>> next();

    @Override
    public void stop() {
        inputBuffer.close();
        desiredInstanceCount.set(0);
        stopped.setRelease(true);
    }

    public void stopFeeding() {
        inputBuffer.close();
        rouseExecutor();
    }

    @Override
    public void join() throws InterruptedException {
        synchronized (this) {
            for (;;) {
                if (instanceCount.get() == 0)
                    return;
                else {
                    wait(1);
                }
            }
        }
    }

    public boolean join(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        long remaining;

        while ((remaining = (end - System.currentTimeMillis())) > 0) {
            if (instanceCount.get() == 0)
                return true;
            else {
                synchronized (this) {
                    wait(remaining);
                }
            }
        }

        return false;
    }

    @Override
    public boolean offer(T val) {
        if (inputBuffer.isClosed()) {
            return false;
        }

        for (int iter = 0; iter < 128; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
        }

        for (int iter = 0; iter < 1024; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
            if (inputBuffer.isClosed())
                return false;

            Thread.yield();
        }

        for (;;) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
            if (inputBuffer.isClosed())
                return false;

            idleSubmitter.compareAndSet(null, Thread.currentThread());
            LockSupport.parkNanos(10_000);
        }
    }


    @Override
    public boolean offer(T val, Duration timeout) {
        for (int iter = 0; iter < 128; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
        }

        long end = System.nanoTime() + timeout.toNanos();

        for (int iter = 0; iter < 1024; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
            if (inputBuffer.isClosed())
                return false;

            Thread.yield();
        }

        for (;;) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
            if (inputBuffer.isClosed())
                return false;
            if (System.nanoTime() > end)
                return false;

            idleSubmitter.compareAndSet(null, Thread.currentThread());
            LockSupport.parkNanos(10_000);
        }
    }

    abstract StageExecution<T> createStage();

    void run() {
        Thread.currentThread().setName("PipeStage:"+stageName);

        StageExecution<T> stage = createStage();

        try {
            outer:
            for (; ; ) {
                if (stopped.getAcquire()) {
                    instanceCount.decrementAndGet();
                    return;
                }

                for (int iter = 0; iter < 128; iter++) {
                    T val = inputBuffer.tryTakeNC();
                    if (val != null) {
                        rouseSubmitter();
                        stage.accept(val);
                        continue outer;
                    }
                    Thread.onSpinWait();
                }

                if (stopped.getAcquire()) {
                    instanceCount.decrementAndGet();
                    return;
                }

                for (int iter = 0; iter < 1024; iter++) {
                    T val = inputBuffer.tryTakeNC();
                    if (val != null) {
                        rouseSubmitter();
                        stage.accept(val);
                        continue outer;
                    }
                    Thread.yield();
                }


                try {
                    idleRunnerCount.incrementAndGet();

                    for (; ; ) {
                        T val = inputBuffer.tryTakeNC();
                        if (val != null) {
                            rouseSubmitter();
                            stage.accept(val);
                            continue outer;
                        }

                        if (stopped.getAcquire()) {
                            instanceCount.decrementAndGet();
                            return;
                        }

                        // Test if we should terminate the instance
                        int inc = instanceCount.get();
                        int dic = desiredInstanceCount.get();
                        if (inc > dic && instanceCount.compareAndSet(inc, inc - 1)) {
                            break outer;
                        }

                        idleRunner.compareAndSet(null, Thread.currentThread());
                        LockSupport.parkNanos(10_000);

                        // Check if the input is closed and we should pack up shop
                        if (inputBuffer.isClosed() && inputBuffer.peek() == null) {
                            instanceCount.decrementAndGet();
                            return;
                        }
                    }
                } finally {
                    idleRunnerCount.decrementAndGet();
                }
            }
        } catch (Throwable t) {
            instanceCount.decrementAndGet();
            logger.info("Exception", t);
            throw t;
        } finally {
            stage.cleanUp();

            if (instanceCount.get() == 0)
                next().ifPresent(PipeStage::stopFeeding);

            synchronized (this) {
                notifyAll();
            }
            Thread.currentThread().setName("IdlePipeStage");
        }
    }

    public boolean isQuiet() {
        return inputBuffer.peek() == null && idleRunnerCount.get() == instanceCount.get();
    }

    protected void rouseSubmitter() {
        Thread thread = idleSubmitter.getAcquire();
        if (thread != null) {
            LockSupport.unpark(thread);
            idleSubmitter.weakCompareAndSetRelease(thread, null);
        }
    }

    protected void rouseExecutor() {
        Thread thread = idleRunner.getAcquire();
        if (thread != null) {
            LockSupport.unpark(thread);
            idleRunner.weakCompareAndSetRelease(thread, null);
        }
    }

    @Override
    public int getInstanceCount() {
        return instanceCount.get();
    }

    @Override
    public void setDesiredInstanceCount(int newDic) {
        desiredInstanceCount.set(newDic);

        int ic;
        while ((ic = instanceCount.get()) < newDic) {
            instanceCount.incrementAndGet();
            executorService.submit(this::run);
        }
    }

}
