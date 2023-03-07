package nu.marginalia.client;

import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Context {
    public static final String CONTEXT_HEADER = "X-Context";
    public static final String SESSION_HEADER = "Cookie";
    public static final String PUBLIC_HEADER = "X-Public";

    private final String id;
    private final String session;
    private boolean treatAsPublic;

    private Context(String id, String session) {
        this.id = Objects.requireNonNull(id, "Context missing");
        this.session = session;
    }

    public Context treatAsPublic() {
        this.treatAsPublic = true;
        return this;
    }

    public static Context internal() {
        return new Context(UUID.randomUUID().toString(), null);
    }
    public static Context internal(String why) {
        return new Context(why + ":" + System.nanoTime(), null);
    }

    public static Context fromRequest(Request request) {

        if (Boolean.getBoolean("unit-test")) {
            return Context.internal();
        }

        final var ctxHeader = anonymizeContext(request);
        final var sessHeader = request.headers(SESSION_HEADER);

        return new Context(ctxHeader, sessHeader);
    }

    private static String anonymizeContext(Request request) {
        String header = request.headers(CONTEXT_HEADER);
        if (header != null && header.contains("-") && !header.startsWith("#")) {
            // The public X-Context header contains info that traces to the
            // external user's IP. Anonymize this by running it through a
            // hash code blender with rotating salt

            return ContextScrambler.anonymize(header, request);
        }
        else if (header != null) {
            return header;
        }
        else {
            // When no X-Context is provided, synthesize one from path
            return request.pathInfo() + ":" + Thread.currentThread().getId();
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

    public String getContextId() {
        return id;
    }

    public boolean isPublic() {
        return id.startsWith("#");
    }

}