package nu.marginalia.geoip;

import nu.marginalia.geoip.AsnTable;
import nu.marginalia.geoip.AsnTable.AsnInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AsnTableTest {

    /**
     * This class is to test the static method parseAsnFileLine of the AsnTable class.
     * This method parses a line from an ASN table file into an AsnInfo instance,
     * which holds ASN number, country and organization string.
     */
    
    @Test
    public void testParseAsnFileLine_ShouldReturnNullWhenUnallocated() {
        String unallocatedLine = "   1 UNALLOCATED";
        AsnInfo result = AsnTable.parseAsnFileLine(unallocatedLine);
        assertNull(result, "Parse ASN File Line output should be null for unallocated ASN");
    }
    
    @Test
    public void testParseAsnFileLine_ShouldReturnNullWhenInputIsNotParsable() {
        String unparsableLine = " NotParsable Line";
        AsnInfo result = AsnTable.parseAsnFileLine(unparsableLine);
        assertNull(result, "Parse ASN File Line output should be null for unparsable lines");
    }

    @Test
    public void testParseAsnFileLine_AllFieldsParsedCorrectly() {
        String asnLineWithAllFields = "123456 Company,US ";
        AsnInfo expected = new AsnInfo(123456, "US", "Company");
        AsnInfo actual = AsnTable.parseAsnFileLine(asnLineWithAllFields);
        assertEquals(expected, actual, "Parse ASN File Line output should match expected AsnInfo when line is correctly formatted");
    }

    @Test
    public void testParseAsnFileLine_MultipleCommasInOrg() {
        String asnLineWithAllFields = "123456 Company, Inc., US ";
        AsnInfo expected = new AsnInfo(123456, "US", "Company, Inc.");
        AsnInfo actual = AsnTable.parseAsnFileLine(asnLineWithAllFields);
        assertEquals(expected, actual, "Parse ASN File Line output should match expected AsnInfo when line is correctly formatted");
    }


    @Test
    public void testParseAsnFileLine_NoCountry() {
        String asnLineWithoutCountry = "123456 Company";
        AsnInfo expected = new AsnInfo(123456, "", "Company");
        AsnInfo actual = AsnTable.parseAsnFileLine(asnLineWithoutCountry);
        assertEquals(expected, actual, "Parse ASN File Line output should match expected AsnInfo when line lacks country");
    }   
}