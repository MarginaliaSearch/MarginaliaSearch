package nu.marginalia.geoip;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class GeoIpDictionaryTest {

    @Test
    public void testAsnResolution() {
        GeoIpDictionary geoIpDictionary = new GeoIpDictionary();
        geoIpDictionary.waitReady();
        System.out.println(geoIpDictionary.getAsnInfo("193.183.0.162"));
    }

}