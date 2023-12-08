package nu.marginalia.crawl.retreival.fetcher.socket;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class IpInterceptingNetworkInterceptor implements Interceptor  {
    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        String IP = chain.connection().socket().getInetAddress().getHostAddress();

        return chain.proceed(chain.request())
                .newBuilder()
                .addHeader("X-Marginalia-Remote-IP", IP)
                .build();
    }

    public static String getIpFromResponse(Response response) {
        return response.header("X-Marginalia-Remote-IP");
    }
}
