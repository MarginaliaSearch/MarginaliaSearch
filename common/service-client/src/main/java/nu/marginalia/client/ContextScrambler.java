package nu.marginalia.client;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ContextScrambler {
    private static final Random random;
    private static final HashFunction hf = Hashing.sha512();
    private static volatile byte[] seed = new byte[12];

    static {
        random = new Random();
        int gr = random.nextInt(10000, 20000);
        for (int i = 0; i < gr; i++) {
            random.nextLong();
        }
        random.nextBytes(seed);

        updateSalt();
    }

    /** Anonymize the string by running it through a hash function
     * together with a salt that is rotated at random intervals.
     * <p/>
     * This is probably not cryptographically secure, but should at least
     * be fairly annoying to reverse-engineer.
     */
    public static String anonymize(String connectionInfo) {
        byte[] hashData = Arrays.copyOf(seed, seed.length+4);
        int hashi = Objects.hash(connectionInfo.split("-", 2)[0]);

        for (int i = 0; i < 4; i++) {
            hashData[seed.length] = (byte)(hashi & 0xFF);
            hashData[seed.length+1] = (byte)(hashi>>>8 & 0xFF);
            hashData[seed.length+2] = (byte)(hashi>>>16 & 0xFF);
            hashData[seed.length+3] = (byte)(hashi>>>24 & 0xFF);
        }

        return String.format("#%x:%x", hf.hashBytes(hashData).asInt(), System.nanoTime() & 0xFFFFFFFFL);
    }

    /** Generate a humongous salt with as many moving parts as possible,
     * as creating a rainbow table of all IP-addresses is fairly easy
     */
    private static byte[] generateSalt() {
        byte[] oldHash = seed;

        int hash1 = random.nextInt();
        int hash2 = hf.hashLong(System.nanoTime()).asInt();
        int hash3 = hf.hashBytes(oldHash).asInt();

        return new byte[]{
                (byte) (hash1 & 0xFF),
                (byte) (hash1 >>> 8 & 0xFF),
                (byte) (hash1 >>> 16 & 0xFF),
                (byte) (hash1 >>> 24 & 0xFF),
                (byte) (hash2 & 0xFF),
                (byte) (hash2 >>> 8 & 0xFF),
                (byte) (hash2 >>> 16 & 0xFF),
                (byte) (hash2 >>> 24 & 0xFF),
                (byte) (hash3 & 0xFF),
                (byte) (hash3 >>> 8 & 0xFF),
                (byte) (hash3 >>> 16 & 0xFF),
                (byte) (hash3 >>> 24 & 0xFF)
        };
    }

    private static void updateSalt() {
        seed = generateSalt();

        int delay = (int) (1000 * (300 + 600*Math.random()));
        Schedulers.computation().scheduleDirect(ContextScrambler::updateSalt, delay, TimeUnit.MILLISECONDS);
    }

}
