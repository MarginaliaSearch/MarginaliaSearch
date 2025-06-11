package nu.marginalia.ping;

import nu.marginalia.ping.fetcher.PingDnsFetcher;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.List;

class PingDnsFetcherTest {
    PingDnsFetcher dnsFetcher;

    @Test
    public void test() throws UnknownHostException {
        dnsFetcher = new PingDnsFetcher(List.of("8.8.8.8"));
        System.out.println(dnsFetcher.dig("marginalia.nu"));
    }
}