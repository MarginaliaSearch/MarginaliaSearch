package nu.marginalia.index.service.util;

import nu.marginalia.util.PrimeUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PrimeUtilTest {

    @Test
    void isPrime() {
        Assertions.assertTrue(PrimeUtil.isPrime(1));
        Assertions.assertTrue(PrimeUtil.isPrime(2));
        Assertions.assertTrue(PrimeUtil.isPrime(3));
        Assertions.assertFalse(PrimeUtil.isPrime(4));
        Assertions.assertTrue(PrimeUtil.isPrime(5));
        Assertions.assertFalse(PrimeUtil.isPrime(6));
        Assertions.assertTrue(PrimeUtil.isPrime(7));
        Assertions.assertFalse(PrimeUtil.isPrime(8));
        Assertions.assertFalse(PrimeUtil.isPrime(9));
        Assertions.assertFalse(PrimeUtil.isPrime(10));
        Assertions.assertTrue(PrimeUtil.isPrime(11));
    }

    @Test
    void nextPrime() {
        System.out.println(PrimeUtil.nextPrime(1L<<31, -1));
        System.out.println(PrimeUtil.nextPrime(1L<<31, 1));

    }
}