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
        Assertions.assertEquals("https://www.example.com/", EdgeUriFactory.uriFromString("https://www.example.com/").toString());
        Assertions.assertEquals("https://www.example.com/", EdgeUriFactory.uriFromString("https://www.example.com/#heredoc").toString());
        Assertions.assertEquals("https://www.example.com/trailingslash/", EdgeUriFactory.uriFromString("https://www.example.com/trailingslash/").toString());
        Assertions.assertEquals("https://www.example.com/%25-sign", EdgeUriFactory.uriFromString("https://www.example.com/%-sign").toString());
        Assertions.assertEquals("https://www.example.com/%22-sign", EdgeUriFactory.uriFromString("https://www.example.com/%22-sign").toString());
        Assertions.assertEquals("https://www.example.com/%0A+%22huh%22", EdgeUriFactory.uriFromString("https://www.example.com/\n \"huh\"").toString());
        Assertions.assertEquals("https://en.wikipedia.org/wiki/S%C3%A1mi", EdgeUriFactory.uriFromString("https://en.wikipedia.org/wiki/Sámi").toString());
    }

    @Test
    void testParms() throws URISyntaxException {
        Assertions.assertEquals("id=123", new EdgeUrl("https://search.marginalia.nu/?id=123").param);
        Assertions.assertEquals("t=123", new EdgeUrl("https://search.marginalia.nu/?t=123").param);
        Assertions.assertEquals("v=123", new EdgeUrl("https://search.marginalia.nu/?v=123").param);
        Assertions.assertEquals("id=1", new EdgeUrl("https://memex.marginalia.nu/showthread.php?id=1&count=5&tracking=123").param);
        Assertions.assertEquals("id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?id=1&t=5&tracking=123").param);
        Assertions.assertEquals("id=1&t=5", new EdgeUrl("https://memex.marginalia.nu/shöwthrëad.php?trëaking=123&id=1&t=5&").param);
        Assertions.assertNull(new EdgeUrl("https://search.marginalia.nu/?m=123").param);
        Assertions.assertNull(new EdgeUrl("https://search.marginalia.nu/?follow=123").param);
    }
}