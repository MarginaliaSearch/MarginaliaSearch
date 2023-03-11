package nu.marginalia.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NextPrimeUtilTest {

    @Test
    void isPrime() {
        Assertions.assertTrue(NextPrimeUtil.isPrime(1));
        Assertions.assertTrue(NextPrimeUtil.isPrime(2));
        Assertions.assertTrue(NextPrimeUtil.isPrime(3));
        Assertions.assertFalse(NextPrimeUtil.isPrime(4));
        Assertions.assertTrue(NextPrimeUtil.isPrime(5));
        Assertions.assertFalse(NextPrimeUtil.isPrime(6));
        Assertions.assertTrue(NextPrimeUtil.isPrime(7));
        Assertions.assertFalse(NextPrimeUtil.isPrime(8));
        Assertions.assertFalse(NextPrimeUtil.isPrime(9));
        Assertions.assertFalse(NextPrimeUtil.isPrime(10));
        Assertions.assertTrue(NextPrimeUtil.isPrime(11));
    }

    @Test
    void nextPrime() {
        System.out.println(NextPrimeUtil.nextPrime(1L<<31, -1));
        System.out.println(NextPrimeUtil.nextPrime(1L<<31, 1));

    }
}