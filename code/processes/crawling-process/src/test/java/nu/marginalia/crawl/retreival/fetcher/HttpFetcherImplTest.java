package nu.marginalia.crawl.retreival.fetcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFetcherImplTest {

    @Test
    public void testXRobotsTag() {
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("foo:"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of(":bar"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of(":"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of(""), "search.marginalia.nu"));

        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("doindex"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("noindex"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("none"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: noindex"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: none"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("googlebot: noindex"), "search.marginalia.nu"));

        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("noindex", "search.marginalia.nu: all"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("none", "search.marginalia.nu: all"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("none", "search.marginalia.nu: none"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("all", "search.marginalia.nu: none"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: all", "noindex"), "search.marginalia.nu"));
        assertTrue(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: all", "none"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: none", "none"), "search.marginalia.nu"));
        assertFalse(HttpFetcherImpl.isXRobotsTagsPermitted(List.of("search.marginalia.nu: none", "all"), "search.marginalia.nu"));
    }

}