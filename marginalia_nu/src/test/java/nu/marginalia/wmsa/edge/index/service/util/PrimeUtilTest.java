package nu.marginalia.wmsa.edge.index.service.util;

import nu.marginalia.util.PrimeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimeUtilTest {

    @Test
    void isPrime() {
        assertTrue(PrimeUtil.isPrime(1));
        assertTrue(PrimeUtil.isPrime(2));
        assertTrue(PrimeUtil.isPrime(3));
        assertFalse(PrimeUtil.isPrime(4));
        assertTrue(PrimeUtil.isPrime(5));
        assertFalse(PrimeUtil.isPrime(6));
        assertTrue(PrimeUtil.isPrime(7));
        assertFalse(PrimeUtil.isPrime(8));
        assertFalse(PrimeUtil.isPrime(9));
        assertFalse(PrimeUtil.isPrime(10));
        assertTrue(PrimeUtil.isPrime(11));
    }

    @Test
    void nextPrime() {
        System.out.println(PrimeUtil.nextPrime(1L<<31, -1));
        System.out.println(PrimeUtil.nextPrime(1L<<31, 1));

    }
}