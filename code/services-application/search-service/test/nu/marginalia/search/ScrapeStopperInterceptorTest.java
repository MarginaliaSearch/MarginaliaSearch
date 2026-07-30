package nu.marginalia.search;

import io.jooby.Context;
import io.jooby.value.Value;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.service.server.RateLimiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScrapeStopperInterceptorTest {

    @BeforeAll
    public static void enableScrapeStopper() {
        System.setProperty("search.useScrapeStopper", "true");
    }

    @AfterAll
    public static void disableScrapeStopper() {
        System.clearProperty("search.useScrapeStopper");
    }

    @Test
    public void testCursor() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("view", "links");
        queryParams.put("cursor", "1234:5678");

        var redirect = interceptTrappedRequest(queryParams);

        assertEquals("?view=links&cursor=1234%3A5678&sst=" + redirect.sst(),
                redirect.redirUrl());
    }

    @Test
    public void testTokenReplace() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("view", "docs");
        queryParams.put("sst", "SI-0000000000000000");

        var redirect = interceptTrappedRequest(queryParams);

        assertEquals("?view=docs&sst=" + redirect.sst(),
                redirect.redirUrl());
    }

    private ScrapeStopperInterceptor.InterceptRedirect interceptTrappedRequest(Map<String, String> queryParams) {
        var interceptor = new ScrapeStopperInterceptor(new ScrapeStopper());

        RateLimiter limiter = RateLimiter.queryPerMinuteLimiter(1);
        limiter.isAllowed();

        var result = interceptor.intercept("SI", "example.com", limiter, mockContext(queryParams));

        return assertInstanceOf(ScrapeStopperInterceptor.InterceptRedirect.class, result);
    }

    private Context mockContext(Map<String, String> queryParams) {
        Value absentValue = mock(Value.class);
        when(absentValue.isPresent()).thenReturn(false);
        when(absentValue.valueOrNull()).thenReturn(null);
        when(absentValue.value("")).thenReturn("");

        Context context = mock(Context.class);
        when(context.header(anyString())).thenReturn(absentValue);
        when(context.cookie(anyString())).thenReturn(absentValue);
        when(context.query(anyString())).thenReturn(absentValue);
        when(context.queryMap()).thenReturn(queryParams);

        return context;
    }
}
