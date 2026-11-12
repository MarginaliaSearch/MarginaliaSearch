package nu.marginalia.coordination;

import nu.marginalia.model.EdgeDomain;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalDomainCoordinatorTest {
    @Test
    void interruptCooldown() {
        checkInterruptedCooldown(false);
    }

    @Test
    void interruptTimedCooldown() {
        checkInterruptedCooldown(true);
    }

    private void checkInterruptedCooldown(boolean timed) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var coordinator = new LocalDomainCoordinator();
            var domain = new EdgeDomain("test.substack.com");
            coordinator.lockDomain(domain).close();
            var interrupted = new AtomicBoolean();

            Thread worker = Thread.ofPlatform().daemon(true).start(() -> {
                try {
                    if (timed) {
                        coordinator.tryLockDomain(domain, Duration.ofSeconds(3)).ifPresent(DomainLock::close);
                    }
                    else {
                        coordinator.lockDomain(domain).close();
                    }
                }
                catch (InterruptedException ex) {
                    interrupted.set(true);
                }
            });

            try {
                while (coordinator.isLockableHint(domain)) {
                    Thread.sleep(1);
                }
                worker.interrupt();
                worker.join();
                assertTrue(interrupted.get());

                var lock = coordinator.tryLockDomain(domain, Duration.ofSeconds(2));
                assertTrue(lock.isPresent());
                lock.ifPresent(DomainLock::close);
            }
            finally {
                worker.interrupt();
            }
        });
    }
}
