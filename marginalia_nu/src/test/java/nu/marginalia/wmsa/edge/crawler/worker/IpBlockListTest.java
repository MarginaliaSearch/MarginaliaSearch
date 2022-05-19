package nu.marginalia.wmsa.edge.crawler.worker;

import com.opencsv.exceptions.CsvValidationException;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

class IpBlockListTest {

    @Test
    void getCountry() throws IOException, CsvValidationException {
        var blocklist = new GeoIpBlocklist();

        String country = blocklist.getCountry(InetAddress.getByName("federali.st"));
        country = blocklist.getCountry(InetAddress.getByName("hugo.md"));
        System.out.println(country);

        country = blocklist.getCountry(InetAddress.getByName("hugo.md"));
        System.out.println(country);
    }

    @Test
    void isAllowed() throws CsvValidationException, IOException {
        var blocklist = new IpBlockList(new GeoIpBlocklist());

//        Assertions.assertFalse(blocklist.isAllowed(new EdgeDomain("localhost")));
//        Assertions.assertFalse(blocklist.isAllowed(new EdgeDomain("www.cloudflare.com")));
//        Assertions.assertTrue(blocklist.isAllowed(new EdgeDomain("https://marginalia.nu")));
        Assertions.assertTrue(blocklist.isAllowed(new EdgeDomain("federali.st")));
        Assertions.assertTrue(blocklist.isAllowed(new EdgeDomain("hugo.md")));
    }
}