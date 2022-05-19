package nu.marginalia.wmsa.auth.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import kotlin.text.Charsets;
import nu.marginalia.wmsa.client.AbstractDynamicClient;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.server.Context;
import org.apache.http.HttpStatus;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;


public class AuthClient extends AbstractDynamicClient {
    @Inject
    public AuthClient() {
        super(ServiceDescriptor.AUTH);
    }

    public Observable<Boolean> isLoggedIn(Context ctx) {
        return get(ctx, "/api/is-logged-in").map(Boolean::parseBoolean);
    }

    public void redirectToLoginIfUnauthenticated(String domain, Request req, Response rsp) {
        if (!isLoggedIn(Context.fromRequest(req)).timeout(1, TimeUnit.SECONDS).blockingFirst()) {
            rsp.redirect(req.headers("X-Extern-Domain") + "/auth/login?service="+domain
                    +"&redirect="+ URLEncoder.encode(req.headers("X-Extern-Url"), Charsets.UTF_8));
            Spark.halt();
        }
    }


    public void requireLogIn(Context ctx) {
        if (!isLoggedIn(ctx).timeout(1, TimeUnit.SECONDS).blockingFirst()) {
            Spark.halt(HttpStatus.SC_FORBIDDEN);
        }
    }
}
