package nu.marginalia.array.pool;

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.StampedLock;

/** Queue for readahead instructions
 * <p></p>
 * Warning:  This class strongly assumes there will only be one thread consuming the
 *           queue.
 * */
public class InstructionsQueue {
    private final long[] addresses;
    private final int queueSize;
    private final StampedLock lock = new StampedLock();

    private int readP = 0;
    private int writeP = 0;

    private Thread waitingConsumer;

    public InstructionsQueue(int queueSize) {
        this.addresses = new long[queueSize];
        this.queueSize = queueSize;

        Arrays.fill(addresses, -1);
    }

    public void enqueue(long address) {

        long stamp = lock.writeLock();
        try {
            if ((writeP + 1) % queueSize == readP)
                return;
            addresses[writeP] =  address;
            writeP = (writeP + 1) % queueSize;

            if (waitingConsumer != null) {
                LockSupport.unpark(waitingConsumer);
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }

    }

    public long dequeue() throws InterruptedException {
        for (;;) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            long stamp = lock.tryOptimisticRead();
            if (readP != writeP) {
                if (!lock.validate(stamp)) continue;

                stamp = lock.writeLock();
                try {
                    if (readP == writeP) return -1;
                    long ret = addresses[readP];
                    readP = (readP + 1) % queueSize;
                    return ret;
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            else {
                boolean waitingConsumerNull = waitingConsumer == null;
                if (!lock.validate(stamp)) continue;

                if (waitingConsumerNull) {
                    stamp = lock.writeLock();
                    try {
                        waitingConsumer = Thread.currentThread();
                    }
                    finally {
                        lock.unlockWrite(stamp);
                    }
                }
                LockSupport.parkNanos(10_000_000L);
            }
        }
    }
}
