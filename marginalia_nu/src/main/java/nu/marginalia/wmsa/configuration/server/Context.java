package nu.marginalia.wmsa.configuration.server;

import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.logging.log4j.ThreadContext;
import spark.Request;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Context {
    public static final String CONTEXT_HEADER = "X-Context";
    public static final String SESSION_HEADER = "Cookie";
    public static final String PUBLIC_HEADER = "X-Public";
    private static final Random random;

    private static volatile byte[] seed = new byte[12];

    private static byte[] generateSalt() {
        byte[] oldHash = seed;

        int hash1 = Long.hashCode(random.nextLong());
        int hash2 = Objects.hash(System.currentTimeMillis());
        int hash3 = Arrays.hashCode(oldHash);

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

    static {
        random = new Random();
        for (int i = 0; i < 1_000_000; i++) {
            random.nextLong();
        }
        random.nextBytes(seed);

        updateSeed();
    }

    private static void updateSeed() {
        seed = generateSalt();

        Schedulers.computation().scheduleDirect(Context::updateSeed,
                60*5000 + (int)(1000*60*10*Math.random()),
                TimeUnit.MILLISECONDS);
    }

    private String id;
    private String session;
    private boolean treatAsPublic;

    private Context(String id, String session) {
        this.id = id;
        this.session = session;
    }

    public Context treatAsPublic() {
        this.treatAsPublic = true;
        return this;
    }

    public static Context internal() {
        return new Context(UUID.randomUUID().toString(), null);
    }
    public static Context internal(String hwat) {
        return new Context(hwat, null);
    }

    public static Context fromRequest(Request request) {

        if (Boolean.getBoolean("unit-test")) {
            return Context.internal();
        }

        final var ctxHeader = hashPublicIp(request.headers(CONTEXT_HEADER));
        final var sessHeader = request.headers(SESSION_HEADER);

        ThreadContext.put("context", ctxHeader+"-"+sessHeader);
        ThreadContext.put("outbound-request", "none");

        return new Context(ctxHeader, sessHeader);
    }

    private static String hashPublicIp(String header) {

        if (header != null && header.contains("-")) {

            byte[] hashData = Arrays.copyOf(seed, seed.length+4);
            int hashi = Objects.hash(header.split("-", 2)[0]);

            for (int i = 0; i < 4; i++) {
                hashData[seed.length] = (byte)(hashi & 0xFF);
                hashData[seed.length+1] = (byte)(hashi>>>8 & 0xFF);
                hashData[seed.length+2] = (byte)(hashi>>>16 & 0xFF);
                hashData[seed.length+3] = (byte)(hashi>>>24 & 0xFF);
            }

            return String.format("#%x", Arrays.hashCode(hashData));
        }
        else {
            return header;
        }
    }

    public okhttp3.Request.Builder paint(okhttp3.Request.Builder requestBuilder) {
        requestBuilder.addHeader(CONTEXT_HEADER, id);

        if (session != null) {
            requestBuilder.addHeader(SESSION_HEADER, session);
        }

        if (treatAsPublic) {
            requestBuilder.header(PUBLIC_HEADER, "1");
        }

        return requestBuilder;
    }

    public Optional<String> getIpHash() {

        if (id.startsWith("#")) {
            return Optional.of(id);
        }

        return Optional.empty();
    }

}