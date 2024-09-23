package nu.marginalia.crawl.fetcher.socket;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


/** An interceptor that intercepts network requests and adds the remote IP address as
 * a header in the response.  This is used to pass the remote IP address to the Warc
 * writer, as this information is not available in the response.
 */
public class IpInterceptingNetworkInterceptor implements Interceptor  {
    private static final String pseudoHeaderName = "X-Marginalia-Remote-IP";

    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        String IP = chain.connection().socket().getInetAddress().getHostAddress();

        return chain.proceed(chain.request())
                .newBuilder()
                .addHeader(pseudoHeaderName, IP)
                .build();
    }

    public static String getIpFromResponse(Response response) {
        return response.header(pseudoHeaderName);
    }
}
