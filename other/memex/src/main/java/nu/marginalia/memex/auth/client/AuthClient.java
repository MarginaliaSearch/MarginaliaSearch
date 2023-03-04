package nu.marginalia.memex.auth.client;

import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import nu.marginalia.WmsaHome;
import nu.marginalia.client.AbstractDynamicClient;
import nu.marginalia.client.Context;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import org.apache.http.HttpStatus;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


public class AuthClient extends AbstractDynamicClient {
    @Inject
    public AuthClient(ServiceDescriptors descriptors) {
        super(descriptors.forId(ServiceId.Other_Auth), WmsaHome.getHostsFile(), new GsonBuilder()::create);
    }

    public Observable<Boolean> isLoggedIn(Context ctx) {
        return get(ctx, "/api/is-logged-in").map(Boolean::parseBoolean);
    }

    public void redirectToLoginIfUnauthenticated(String domain, Request req, Response rsp) {
        if (!isLoggedIn(Context.fromRequest(req)).timeout(1, TimeUnit.SECONDS).blockingFirst()) {
            rsp.redirect(req.headers("X-Extern-Domain") + "/auth/login?service="+domain
                    +"&redirect="+ URLEncoder.encode(req.headers("X-Extern-Url"), StandardCharsets.UTF_8));
            Spark.halt();
        }
    }


    public void requireLogIn(Context ctx) {
        if (!isLoggedIn(ctx).timeout(1, TimeUnit.SECONDS).blockingFirst()) {
            Spark.halt(HttpStatus.SC_FORBIDDEN);
        }
    }
}
