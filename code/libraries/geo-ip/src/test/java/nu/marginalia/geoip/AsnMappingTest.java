package nu.marginalia.geoip;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AsnMappingTest {
    @Test
    public void testParseAsnMappingFileLine() throws UnknownHostException {
        // Test Case 1: Valid ASN Mapping Line
        String input1 = "192.0.2.0/24\t65536";
        AsnMapping.AsnMappingRecord result1 = AsnMapping.parseAsnMappingFileLine(input1);
        assertNotNull(result1, "The result should not be null for valid data");
        assertEquals(65536, result1.asn(), "The asn is not as expected");

        // Test Case 2: Invalid ASN Mapping Line - Different format
        String input2 = "nah I am just a string, not an ASN Mapping Line...";
        AsnMapping.AsnMappingRecord result2 = AsnMapping.parseAsnMappingFileLine(input2);
        assertNull(result2, "The result should be null for invalid data");

        // Test Case 3: Invalid ASN Mapping Line - Null input
        String input3 = null;
        AsnMapping.AsnMappingRecord result3 = AsnMapping.parseAsnMappingFileLine(input3);
        assertNull(result3, "The result should be null for null input");

        // Test Case 4: Invalid ASN Mapping Line - Empty string
        String input4 = "";
        AsnMapping.AsnMappingRecord result4 = AsnMapping.parseAsnMappingFileLine(input4);
        assertNull(result4, "The result should be null for empty string");

        // Test Case 5: Invalid ASN Mapping Line - One part
        String input5 = "192.0.2.0/24";
        AsnMapping.AsnMappingRecord result5 = AsnMapping.parseAsnMappingFileLine(input5);
        assertNull(result5, "The result should be null for a string with only one part");

    }

    @Test
    public void testIpBounds() throws UnknownHostException {
        String input7 = "193.183.0.0/24\t207825";
        AsnMapping.AsnMappingRecord result7 = AsnMapping.parseAsnMappingFileLine(input7);
        assertNotNull(result7, "The result should not be null for valid data");
        var ip = InetAddress.getAllByName("193.183.0.0");
        byte[] ipBytes = ip[0].getAddress();

        int ipInt = (int) (((long)ipBytes[0]&0xFF) << 24 | ((long)ipBytes[1]&0xFF) << 16 | ((long)ipBytes[2]&0xFF)<< 8 | ((long)ipBytes[3]&0xFF));

        assertTrue(result7.contains(ipInt));
    }
}