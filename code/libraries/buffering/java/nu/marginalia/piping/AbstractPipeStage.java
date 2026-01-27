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

    private final long startTimeNanos;
    private final long maxRunDurationNanos;

    protected AbstractPipeStage(String stageName,
                                int size,
                                Duration maxRunDuration,
                                ExecutorService executorService)
    {
        startTimeNanos = System.nanoTime();
        maxRunDurationNanos = maxRunDuration.toNanos();

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
        stopped.set(true);
    }

    public void stopFeeding() {
        inputBuffer.close();
        rouseExecutor();

        if (instanceCount.get() == 0) {
            next().ifPresent(PipeStage::stopFeeding);
        }
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

            if (System.nanoTime() - startTimeNanos > maxRunDurationNanos) {
                stopped.set(true);
            }

            if (inputBuffer.isClosed() || stopped.getAcquire())
                return false;

            idleSubmitter.compareAndSet(null, Thread.currentThread());
            LockSupport.parkNanos(10_000);
        }
    }


    @Override
    public boolean offer(T val, Duration timeout) {
        long start = System.nanoTime();

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
            if (inputBuffer.isClosed() || stopped.get())
                return false;

            Thread.yield();
        }

        long timeoutNs = timeout.toNanos();

        for (;;) {
            if (inputBuffer.putNP(val)) {
                rouseExecutor();
                return true;
            }
            if (inputBuffer.isClosed() || stopped.get())
                return false;

            if (System.nanoTime() - start > timeoutNs)
                return false;

            if (System.nanoTime() - startTimeNanos > maxRunDurationNanos)
                stopped.set(true);

            idleSubmitter.compareAndSet(null, Thread.currentThread());
            LockSupport.parkNanos(10_000);
        }
    }

    abstract StageExecution<T> createStage();

    void run() {
        String originalThreadName = Thread.currentThread().getName();
        StageExecution<T> stage = null;

        try {
            Thread.currentThread().setName("PipeStage:"+stageName);

            stage = createStage();

            outer:
            for (; ; ) {
                if (stopped.getAcquire()) return;

                for (int iter = 0; iter < 128; iter++) {
                    T val = inputBuffer.tryTakeNC();
                    if (val != null) {
                        rouseSubmitter();
                        stage.accept(val);
                        continue outer;
                    }
                    Thread.onSpinWait();
                }

                if (stopped.getAcquire()) return;

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

                        if (System.nanoTime() - startTimeNanos > maxRunDurationNanos) {
                            stopped.set(true);
                        }

                        if (stopped.getAcquire()) return;

                        idleRunner.compareAndSet(null, Thread.currentThread());
                        LockSupport.parkNanos(10_000);

                        // Check if the input is closed and we should pack up shop
                        if (inputBuffer.isClosed() && inputBuffer.peek() == null)
                            return;

                    }
                } finally {
                    idleRunnerCount.decrementAndGet();
                }
            }
        } catch (Throwable t) {
            logger.info("Exception", t);
            throw t;
        } finally {
            try {
                if (stage != null) {
                    stage.cleanUp();
                }
            }
            catch (Throwable t) {
                logger.error("Error in clean up for stage {}", stageName, t);
            }

            if (instanceCount.decrementAndGet() == 0)
                next().ifPresent(PipeStage::stopFeeding);

            synchronized (this) {
                notifyAll();
            }

            Thread.currentThread().setName(originalThreadName);
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
        int oldDic = desiredInstanceCount.get();
        if (newDic < oldDic)
            throw new IllegalArgumentException("Instance count can not be shrunk");

        desiredInstanceCount.set(newDic);

        int toSubmit = newDic - oldDic;
        for (int i = 0; i < toSubmit; i++) {
            executorService.execute(this::run);
            instanceCount.incrementAndGet();
        }
    }

}
