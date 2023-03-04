package nu.marginalia.util;

// This is not a fast way of finding primes
public class PrimeUtil {

    public static long nextPrime(long start, long step) {
        if (isDivisible(start, 2)) {
            start = start + step;
        }

        long val;
        for (val = start; !isPrime(val); val += 2*step) {}
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

    public static boolean isDivisible(long a, long b) {
        if (a == 0 || b == 0) {
            return false;
        }

        if (a > b) {
            return (a % b) == 0;
        }
        return (b % a) == 0;
    }
}
