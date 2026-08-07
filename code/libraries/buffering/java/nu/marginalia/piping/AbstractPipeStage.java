package nu.marginalia.piping;

import nu.marginalia.buffering.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public abstract class AbstractPipeStage<T> implements PipeStage<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** Park with exponential backoff between these bounds when idle.  Parked
     *  threads are unparked when work or space appears, so the backoff only
     *  bounds the staleness of a missed wakeup, not the handoff latency. */
    private static final long MIN_PARK_NANOS = 20_000;
    private static final long MAX_PARK_NANOS = 2_000_000;

    public final String stageName;

    private final RingBuffer<T> inputBuffer;
    private final ExecutorService executorService;
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final ConcurrentLinkedQueue<Thread> idleSubmitters = new ConcurrentLinkedQueue<>();
    private final AtomicInteger idleRunnerCount = new AtomicInteger();

    private final List<Thread> runnerThreads = new CopyOnWriteArrayList<>();

    private final long startTimeNanos;
    private final long maxRunDurationNanos;

    private final CountDownLatch countdown;

    protected AbstractPipeStage(String stageName,
                                int size,
                                int threads,
                                Duration maxRunDuration,
                                ExecutorService executorService)
    {
        startTimeNanos = System.nanoTime();
        maxRunDurationNanos = maxRunDuration.toNanos();

        this.stageName = stageName;
        this.inputBuffer = new RingBuffer<>(size);
        this.executorService = executorService;

        countdown = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            try {
                executorService.execute(this::run);
            }
            catch (Throwable t) {
                logger.error("Error starting thread", t);
                countdown.countDown();
            }
        }
    }

    @Override
    public abstract Optional<PipeStage<?>> next();

    @Override
    public void stop() {
        inputBuffer.close();
        stopped.set(true);
        rouseExecutors();
    }

    public void stopFeeding() {
        inputBuffer.close();
        rouseExecutors();

        if (countdown.getCount() == 0) {
            next().ifPresent(PipeStage::stopFeeding);
        }
    }

    @Override
    public void join() throws InterruptedException {
        countdown.await();
    }

    public boolean join(long millis) throws InterruptedException {
        return countdown.await(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean offer(T val) {
        if (inputBuffer.isClosed()) {
            return false;
        }

        for (int iter = 0; iter < 128; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseIdleExecutors();
                return true;
            }
            Thread.onSpinWait();
        }

        long parkTime = MIN_PARK_NANOS;
        for (;;) {
            if (inputBuffer.putNP(val)) {
                rouseExecutors();
                return true;
            }

            if (System.nanoTime() - startTimeNanos > maxRunDurationNanos) {
                stopped.set(true);
            }

            if (inputBuffer.isClosed() || stopped.getAcquire())
                return false;

            Thread self = Thread.currentThread();
            idleSubmitters.add(self);
            LockSupport.parkNanos(parkTime);
            idleSubmitters.remove(self);
            parkTime = Math.min(2 * parkTime, MAX_PARK_NANOS);
        }
    }


    @Override
    public boolean offer(T val, Duration timeout) {
        long start = System.nanoTime();

        for (int iter = 0; iter < 128; iter++) {
            if (inputBuffer.putNP(val)) {
                rouseIdleExecutors();
                return true;
            }
            Thread.onSpinWait();
        }

        long timeoutNs = timeout.toNanos();

        long parkTime = MIN_PARK_NANOS;
        for (;;) {
            if (inputBuffer.putNP(val)) {
                rouseExecutors();
                return true;
            }
            if (inputBuffer.isClosed() || stopped.get())
                return false;

            if (System.nanoTime() - start > timeoutNs)
                return false;

            if (System.nanoTime() - startTimeNanos > maxRunDurationNanos)
                stopped.set(true);

            Thread self = Thread.currentThread();
            idleSubmitters.add(self);
            LockSupport.parkNanos(parkTime);
            idleSubmitters.remove(self);
            parkTime = Math.min(2 * parkTime, MAX_PARK_NANOS);
        }
    }

    abstract StageExecution<T> createStage();

    void run() {
        String originalThreadName = Thread.currentThread().getName();
        StageExecution<T> stage = null;

        runnerThreads.add(Thread.currentThread());

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

                try {
                    idleRunnerCount.incrementAndGet();

                    long parkTime = MIN_PARK_NANOS;
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

                        LockSupport.parkNanos(parkTime);
                        parkTime = Math.min(2 * parkTime, MAX_PARK_NANOS);

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
            finally {
                countdown.countDown();

                if (countdown.getCount() == 0) {
                    next().ifPresent(PipeStage::stopFeeding);
                }

                Thread.currentThread().setName(originalThreadName);
            }
        }
    }

    protected void rouseSubmitter() {
        Thread thread = idleSubmitters.poll();
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    protected void rouseExecutors() {
        runnerThreads.forEach(LockSupport::unpark);
    }

    /** As rouseExecutors, but only when a runner is in its park loop.  The offer
     *  fast path takes this branch, so the check must stay a cheap read in the
     *  common case where all runners are busy. */
    private void rouseIdleExecutors() {
        if (idleRunnerCount.getAcquire() > 0) {
            rouseExecutors();
        }
    }


}
