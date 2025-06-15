package nu.marginalia.ping.io;

import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class RetryStrategy implements HttpRequestRetryStrategy {
    private static final Logger logger = LoggerFactory.getLogger(RetryStrategy.class);

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int executionCount, HttpContext context) {
        return switch (exception) {
            case SocketTimeoutException ste -> false;
            case SSLException ssle -> false;
            case UnknownHostException uhe -> false;
            case HttpHostConnectException ex -> executionCount <= 2; // Only retry once for connection errors
            default -> executionCount <= 3;
        };
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        return switch (response.getCode()) {
            case 500, 503 -> executionCount <= 2;
            case 429 -> executionCount <= 3;
            default -> false;
        };
    }

    @Override
    public TimeValue getRetryInterval(HttpRequest request, IOException exception, int executionCount, HttpContext context) {
        return TimeValue.ofSeconds(1);
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int executionCount, HttpContext context) {

        int statusCode = response.getCode();

        // Give 503 a bit more time
        if (statusCode == 503) return TimeValue.ofSeconds(5);

        if (statusCode == 429) {
            // get the Retry-After header
            var retryAfterHeader = response.getFirstHeader("Retry-After");
            if (retryAfterHeader == null) {
                return TimeValue.ofSeconds(3);
            }

            String retryAfter = retryAfterHeader.getValue();
            if (retryAfter == null) {
                return TimeValue.ofSeconds(2);
            }

            try {
                int retryAfterTime = Integer.parseInt(retryAfter);
                retryAfterTime = Math.clamp(retryAfterTime, 1, 5);

                return TimeValue.ofSeconds(retryAfterTime);
            } catch (NumberFormatException e) {
                logger.warn("Invalid Retry-After header: {}", retryAfter);
            }
        }

        return TimeValue.ofSeconds(2);
    }
}
