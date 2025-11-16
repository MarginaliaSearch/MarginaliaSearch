package nu.marginalia.searchfilter;

import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class SearchFilterParserTest {
    SearchFilterParser parser = new SearchFilterParser();

    @Test
    public void parseDomainLists() throws SearchFilterParser.SearchFilterParserException {
        var filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                    <domains-include>
                        www.google.com
                        *.youtube.com
                    </domains-include>
                
                    <domains-exclude>
                        www.yandex.ru
                    </domains-exclude>
                
                    <domains-promote amount="-1.0">
                        www.bing.com
                        www.yahoo.com
                    </domains-promote>

                    <domains-promote amount="1.0">
                        www.mojeek.com
                    </domains-promote>
                </filter>
                """);
        List<String> expectedIncludes = List.of("www.google.com", "*.youtube.com");
        List<String> expectedExcludes = List.of("www.yandex.ru");
        List<Map.Entry<String, Float>> expectedPromotes =
                List.of(
                        Map.entry("www.bing.com", -1.0f),
                        Map.entry("www.yahoo.com", -1.0f),
                        Map.entry("www.mojeek.com", 1.0f)
                );

        Assertions.assertEquals(expectedIncludes, filter.domainsInclude());
        Assertions.assertEquals(expectedExcludes, filter.domainsExclude());
        Assertions.assertEquals(expectedPromotes, filter.domainsPromote());
        Assertions.assertTrue(filter.termsRequire().isEmpty());
        Assertions.assertTrue(filter.termsExclude().isEmpty());
        Assertions.assertTrue(filter.termsPromote().isEmpty());
    }

    @Test
    public void parseSearchSet() throws SearchFilterParser.SearchFilterParserException {
        var filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                    <search-set>BLOGS</search-set>
                </filter>
                """);
        Assertions.assertEquals("BLOGS", filter.searchSetIdentifier());
    }


    @Test
    public void temporalBiasTest() throws SearchFilterParser.SearchFilterParserException {
        var filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                </filter>
                """);
        Assertions.assertEquals("NONE", filter.temporalBias());

        filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                   <temporal-bias> Recent </temporal-bias>
                </filter>
                """);
        Assertions.assertEquals("RECENT", filter.temporalBias());


        filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                   <temporal-bias> old </temporal-bias>
                </filter>
                """);
        Assertions.assertEquals("OLD", filter.temporalBias());

        try {
            parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                   <temporal-bias> Dog </temporal-bias>
                </filter>
                """);
            Assertions.fail("Expected exception");
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {}

        try {
            parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                   <temporal-bias> OLD </temporal-bias>
                   <temporal-bias> RECENT </temporal-bias>
                </filter>
                """);
            Assertions.fail("Expected exception");
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {}
    }

    @Test
    public void parseSearchSetAndIncludes() {
        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <domains-include>
                            www.google.com
                            *.youtube.com
                        </domains-include>
                        <search-set>BLOGS</search-set>
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            // OK path
        }
    }

    @Test
    public void parseTermsLists() throws SearchFilterParser.SearchFilterParserException {
        var filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                    <terms-require>
                        foo
                        bar
                    </terms-require>
                
                    <terms-exclude>
                        baz
                    </terms-exclude>
                
                    <terms-promote amount="5.0">
                        quux
                    </terms-promote>
                
                </filter>
                """);
        List<String> expectedIncludes = List.of("foo", "bar");
        List<String> expectedExcludes = List.of("baz");
        List<Map.Entry<String, Float>> expectedPromotes = List.of(Map.entry("quux", 5.0f));

        Assertions.assertEquals(expectedIncludes, filter.termsRequire());
        Assertions.assertEquals(expectedExcludes, filter.termsExclude());
        Assertions.assertEquals(expectedPromotes, filter.termsPromote());
        Assertions.assertTrue(filter.domainsInclude().isEmpty());
        Assertions.assertTrue(filter.domainsExclude().isEmpty());
        Assertions.assertTrue(filter.domainsPromote().isEmpty());
    }

    @Test
    public void parseLimitsMapping() throws SearchFilterParser.SearchFilterParserException {
        var filter = parser.parse("test", "test", """
                <?xml version="1.0"?>
                <filter>
                    <year type="lt" value="1996" />
                    <quality type="eq" value="5" />
                    <size type="gt" value="100" />
                </filter>
                """);

        Assertions.assertEquals(SpecificationLimit.lessThan(1996), filter.year());
        Assertions.assertEquals(SpecificationLimit.equals(5), filter.quality());
        Assertions.assertEquals(SpecificationLimit.greaterThan(100), filter.size());
    }

    @Test
    public void parseLimitsErrorHandling() {
        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <year value="1996" />
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            ex.printStackTrace();
            // OK path
        }

        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <year type="dog" value="1996" />
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            ex.printStackTrace();
            // OK path
        }

        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <year type="eq" value="cat" />
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            ex.printStackTrace();
            // OK path
        }

        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <year type="eq" value="" />
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            ex.printStackTrace();
            // OK path
        }

        try {
            var ret = parser.parse("test", "test", """
                    <?xml version="1.0"?>
                    <filter>
                        <year type="eq" />
                    </filter>
                    """);
            Assertions.fail("Expected exception, got " + ret);
        }
        catch (SearchFilterParser.SearchFilterParserException ex) {
            ex.printStackTrace();
            // OK path
        }
    }
}