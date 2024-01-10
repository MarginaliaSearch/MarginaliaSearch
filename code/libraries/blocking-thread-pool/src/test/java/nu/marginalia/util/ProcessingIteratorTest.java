package nu.marginalia.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingIteratorTest {

    @Test
    public void test() {
        Set<Integer> output = new HashSet<>();
        Iterator<Integer> iter = ProcessingIterator.factory(2, 2).create(q -> {
            for (int i = 0; i < 10_000; i++) {
                int j = i;
                q.accept(() -> task(j));
            }
        });
        while (iter.hasNext()) {
            output.add(iter.next());
        }

        assertEquals(10_000, output.size());

        for (int i = 0; i < 10_000; i++) {
            assertTrue(output.contains(i));
        }
    }

    int task(int n) throws InterruptedException {
        TimeUnit.NANOSECONDS.sleep(10);
        return n;
    }
}