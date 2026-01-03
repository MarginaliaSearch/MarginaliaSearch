package nu.marginalia.search.command.commands;

import nu.marginalia.search.command.SearchParameters;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SiteRedirectCommandTest {

    SiteRedirectCommand command = new SiteRedirectCommand();

    @Test
    void testFullUrlRedirect() {
        Assertions.assertEquals(Optional.of("marginalia.nu"), testAndGetRedirect("site:marginalia.nu"));
        Assertions.assertEquals(Optional.of("marginalia.nu"), testAndGetRedirect("site:https://marginalia.nu/"));
        Assertions.assertEquals(Optional.of("marginalia.nu"), testAndGetRedirect("site:http://marginalia.nu/"));
        Assertions.assertEquals(Optional.of("marginalia.nu"), testAndGetRedirect("site:https://marginalia.nu"));
        Assertions.assertEquals(Optional.of("marginalia.nu"), testAndGetRedirect("site:https://marginalia.nu/index.html"));
    }

    Optional<String> testAndGetRedirect(String query) {
        var resOpt = command.process(null, SearchParameters.defaultsForQuery(query, 1));
        if (resOpt.isEmpty())
            return Optional.empty();
        var res = (String) resOpt.get();

        System.out.println(res);
        var doc = Jsoup.parse(res);
        var ret = doc.getElementsByTag("meta").attr("content");

        ret = ret.substring("0; url=/site/".length());
        ret = ret.substring(0, ret.indexOf('?'));
        return Optional.of(ret);
    }

}