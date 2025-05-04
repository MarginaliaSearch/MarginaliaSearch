package nu.marginalia.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgeUrlTest {

    @Test
    public void testHashCode() throws URISyntaxException {
        System.out.println(new EdgeUrl("https://memex.marginalia.nu").hashCode());
    }

    @Test
    public void testFragment() throws URISyntaxException {
        assertEquals(
                new EdgeUrl("https://memex.marginalia.nu/"),
                new EdgeUrl("https://memex.marginalia.nu/#here")
        );
    }

    @Test
    void testUriFromString() throws URISyntaxException {
        // We test these URLs several times as we perform URLEncode-fixing both when parsing the URL and when
        // converting it back to a string, we want to ensure there is no changes along the way.

        Assertions.assertEquals("/", EdgeUriFactory.parseURILenient("https://www.example.com/").getPath());
        Assertions.assertEquals("https://www.example.com/", EdgeUriFactory.parseURILenient("https://www.example.com/").toString());
        Assertions.assertEquals("https://www.example.com/", new EdgeUrl("https://www.example.com/").toString());

        Assertions.assertEquals("/", EdgeUriFactory.parseURILenient("https://www.example.com/#heredoc").getPath());
        Assertions.assertEquals("https://www.example.com/", EdgeUriFactory.parseURILenient("https://www.example.com/#heredoc").toString());
        Assertions.assertEquals("https://www.example.com/", new EdgeUrl("https://www.example.com/#heredoc").toString());

        Assertions.assertEquals("/trailingslash/", EdgeUriFactory.parseURILenient("https://www.example.com/trailingslash/").getPath());
        Assertions.assertEquals("https://www.example.com/trailingslash/", EdgeUriFactory.parseURILenient("https://www.example.com/trailingslash/").toString());
        Assertions.assertEquals("https://www.example.com/trailingslash/", new EdgeUrl("https://www.example.com/trailingslash/").toString());

        Assertions.assertEquals("/%-sign", EdgeUriFactory.parseURILenient("https://www.example.com/%-sign").getPath());
        Assertions.assertEquals("https://www.example.com/%25-sign", EdgeUriFactory.parseURILenient("https://www.example.com/%-sign").toString());
        Assertions.assertEquals("https://www.example.com/%25-sign", new EdgeUrl("https://www.example.com/%-sign").toString());

        Assertions.assertEquals("/%-sign/\"-sign", EdgeUriFactory.parseURILenient("https://www.example.com//%-sign/\"-sign").getPath());
        Assertions.assertEquals("https://www.example.com/%25-sign/%22-sign", EdgeUriFactory.parseURILenient("https://www.example.com//%-sign/\"-sign").toString());
        Assertions.assertEquals("https://www.example.com/%25-sign/%22-sign", new EdgeUrl("https://www.example.com//%-sign/\"-sign").toString());

        Assertions.assertEquals("/\"-sign", EdgeUriFactory.parseURILenient("https://www.example.com/%22-sign").getPath());
        Assertions.assertEquals("https://www.example.com/%22-sign", EdgeUriFactory.parseURILenient("https://www.example.com/%22-sign").toString());
        Assertions.assertEquals("https://www.example.com/%22-sign", new EdgeUrl("https://www.example.com/%22-sign").toString());

        Assertions.assertEquals("/\n \"huh\"", EdgeUriFactory.parseURILenient("https://www.example.com/\n \"huh\"").getPath());
        Assertions.assertEquals("https://www.example.com/%0A%20%22huh%22", EdgeUriFactory.parseURILenient("https://www.example.com/\n \"huh\"").toString());
        Assertions.assertEquals("https://www.example.com/%0A%20%22huh%22", new EdgeUrl("https://www.example.com/\n \"huh\"").toString());

        Assertions.assertEquals("/wiki/Sámi", EdgeUriFactory.parseURILenient("https://en.wikipedia.org/wiki/Sámi").getPath());
        Assertions.assertEquals("https://en.wikipedia.org/wiki/S%C3%A1mi", EdgeUriFactory.parseURILenient("https://en.wikipedia.org/wiki/Sámi").toString());
        Assertions.assertEquals("https://en.wikipedia.org/wiki/S%C3%A1mi", new EdgeUrl("https://en.wikipedia.org/wiki/Sámi").toString());

        Assertions.assertEquals("https://www.prijatelji-zivotinja.hr/index.en.php?id=2301k", new EdgeUrl("https://www.prijatelji-zivotinja.hr/index.en.php?id=2301k").toString());
    }

    @Test
    void testParms() throws URISyntaxException {
        Assertions.assertEquals("id=123", new EdgeUrl("https://search.marginalia.nu/?id=123").param);
        Assertions.assertEquals("https://search.marginalia.nu/?id=123", new EdgeUrl("https://search.marginalia.nu/?id=123").toString());

        Assertions.assertEquals("t=123", new EdgeUrl("https://search.marginalia.nu/?t=123").param);
        Assertions.assertEquals("https://search.marginalia.nu/?t=123", new EdgeUrl("https://search.marginalia.nu/?t=123").toString());

        Assertions.assertEquals("v=123", new EdgeUrl("https://search.marginalia.nu/?v=123").param);
        Assertions.assertEquals("https://search.marginalia.nu/?v=123", new EdgeUrl("https://search.marginalia.nu/?v=123").toString());

        Assertions.assertEquals("id=1", new EdgeUrl("https://memex.marginalia.nu/showthread.php?id=1&count=5&tracking=123").param);
        Assertions.assertEquals("https://memex.marginalia.nu/showthread.php?id=1",
                new EdgeUrl("https://memex.marginalia.nu/showthread.php?id=1&count=5&tracking=123").toString());


        Assertions.assertEquals("id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?id=1&t=5&tracking=123").param);
        Assertions.assertEquals("https://memex.marginalia.nu/sh%C3%B6wthr%C3%ABad.php?id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?id=1&t=5&tracking=123").toString());

        Assertions.assertEquals("id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?trëaking=123&id=1&t=5&").param);
        Assertions.assertEquals("https://memex.marginalia.nu/sh%C3%B6wthr%C3%ABad.php?id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?trëaking=123&id=1&t=5&").toString());

        Assertions.assertNull(new EdgeUrl("https://search.marginalia.nu/?m=123").param);
        Assertions.assertNull(new EdgeUrl("https://search.marginalia.nu/?follow=123").param);
    }
}