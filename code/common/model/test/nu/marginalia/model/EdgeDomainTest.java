package nu.marginalia.model;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgeDomainTest {

    @Test
    public void testSkepdic() throws URISyntaxException {
        var domain = new EdgeUrl("http://www.skepdic.com/astrology.html");
        assertEquals("skepdic", domain.getDomain().getDomainKey());
        var domain2 = new EdgeUrl("http://skepdic.com/astrology.html");
        assertEquals("skepdic", domain2.getDomain().getDomainKey());
    }

    @Test
    public void testHkDomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://l7072i3.l7c.net");
        assertEquals("http", domain.proto);
        assertEquals("l7072i3", domain.domain.subDomain);
        assertEquals("l7c.net", domain.domain.topDomain);
        assertEquals("net", domain.domain.getTld());
    }

    @Test
    public void testEndlessHorse() throws URISyntaxException {
        var domain = new EdgeUrl("http://endless.horse/");
        assertEquals("http", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("endless.horse", domain.domain.topDomain);
        assertEquals("horse", domain.domain.getTld());
    }

    @Test
    public void testEduSubDomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://uj.edu.pl");
        assertEquals("http", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("uj.edu.pl", domain.domain.topDomain);
        assertEquals("edu.pl", domain.domain.getTld());
    }


    @Test
    public void testGetDomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://www.marginalia.nu");
        assertEquals("http", domain.proto);
        assertEquals("www", domain.domain.subDomain);
        assertEquals("marginalia.nu", domain.domain.topDomain);
        assertEquals("http://www.marginalia.nu/", domain.toString());
        assertEquals("nu", domain.domain.getTld());
    }

    @Test
    public void testUkDomain2() throws URISyntaxException {
        var domain = new EdgeUrl("http://marginalia.co.uk");
        assertEquals("marginalia.co.uk", domain.domain.topDomain);
        assertEquals("http", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("http://marginalia.co.uk/", domain.toString());
        assertEquals("co.uk", domain.domain.getTld());
    }

    @Test
    public void testUkDomain3() throws URISyntaxException {
        var domain = new EdgeUrl("http://withcandour.co.uk");
        assertEquals("withcandour.co.uk", domain.domain.topDomain);
        assertEquals("http", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("http://withcandour.co.uk/", domain.toString());
        assertEquals("co.uk", domain.domain.getTld());
    }

    @Test
    public void testUkDomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://www.marginalia.co.uk");
        assertEquals("http", domain.proto);
        assertEquals("www", domain.domain.subDomain);
        assertEquals("marginalia.co.uk", domain.domain.topDomain);
        assertEquals("http://www.marginalia.co.uk/", domain.toString());
    }

    @Test
    public void testThreeLetterDomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://www.marginalia.abcf.de");
        assertEquals("http", domain.proto);
        assertEquals("abcf.de", domain.domain.topDomain);
        assertEquals("www.marginalia", domain.domain.subDomain);
        assertEquals("de", domain.domain.getTld());
    }

    @Test
    public void testGetDomainNoSubdomain() throws URISyntaxException {
        var domain = new EdgeUrl("http://marginalia.nu");
        assertEquals("http", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("marginalia.nu", domain.domain.topDomain);
        assertEquals("http://marginalia.nu/", domain.toString());
        assertEquals("nu", domain.domain.getTld());
    }

    @Test
    public void testIpPort() throws URISyntaxException {
        var domain = new EdgeUrl("https://127.0.0.1:8080");
        assertEquals("https", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("127.0.0.1", domain.domain.topDomain);
        assertEquals("https://127.0.0.1:8080/", domain.toString());
        assertEquals("IP", domain.domain.getTld());
    }

    @Test
    public void testIp() throws URISyntaxException {
        var domain = new EdgeUrl("https://192.168.1.32");
        assertEquals("https", domain.proto);
        assertEquals("", domain.domain.subDomain);
        assertEquals("192.168.1.32", domain.domain.topDomain);
        assertEquals("https://192.168.1.32/", domain.toString());
        assertEquals("IP", domain.domain.getTld());
    }
}