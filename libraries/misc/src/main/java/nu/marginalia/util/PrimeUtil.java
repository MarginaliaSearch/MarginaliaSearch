package nu.marginalia.util;

// This is not a fast way of finding primes
public class PrimeUtil {

    /** Returns the next prime value starting at start. If start is prime, return start.
     */
    public static long nextPrime(long start, long direction) {
        if (isCoprime(start, 2)) {
            start = start + direction;
        }

        long val;
        for (val = start; !isPrime(val); val += 2*direction) {}
        return val;
    }

    public static boolean isPrime(long v) {
        if (v <= 2) {
            return true;
        }
        if ((v & 1) == 0) {
            return false;
        }
        for (long t = 3; t <= v/3; t++) {
            if ((v % t) == 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCoprime(long a, long b) {
        if (a == 0 || b == 0) {
            return false;
        }

        if (a > b) {
            return (a % b) == 0;
        }
        return (b % a) == 0;
    }
}
