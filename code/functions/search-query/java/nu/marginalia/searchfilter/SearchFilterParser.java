package nu.marginalia.searchfilter;

import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.searchfilter.model.SearchFilterSpec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchFilterParser {
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    public static class SearchFilterParserException extends Exception {
        public SearchFilterParserException(String message) { super(message); }
        public SearchFilterParserException(String message, Throwable cause) { super(message, cause); }
    }

    public SearchFilterParser() {

    }

    public SearchFilterSpec parse(String userId, String identifier, String xml)
            throws SearchFilterParserException
    {
        final List<String> domainsInclude;
        final List<String> domainsExclude;
        final List<Map.Entry<String, Float>> domainsPromote;
        final List<Map.Entry<String, Float>> termsPromote;
        final List<String> termsRequire;
        final List<String> termsExclude;

        final String searchSetIdentifier;

        final SpecificationLimit year;
        final SpecificationLimit size;
        final SpecificationLimit quality;

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList filterTag = doc.getElementsByTagName("filter");

            if (filterTag.getLength() == 0)
                throw new SearchFilterParserException("Missing filter tag");
            else if (filterTag.getLength() != 1)
                throw new SearchFilterParserException("Multiple filter tags");

            var filters = (Element) filterTag.item(0);

            domainsInclude = extractContentList(filters.getElementsByTagName("domains-include"));
            domainsExclude = extractContentList(filters.getElementsByTagName("domains-exclude"));
            domainsPromote = extractPromoteList(filters.getElementsByTagName("domains-promote"), "amount");

            NodeList searchSetIdentifierList = filters.getElementsByTagName("search-set");

            searchSetIdentifier = switch(searchSetIdentifierList.getLength()) {
                case 0 -> null;
                case 1 -> searchSetIdentifierList.item(0).getTextContent();
                default -> throw new SearchFilterParserException("Multiple search-set tags");
            };

            termsRequire = extractContentList(filters.getElementsByTagName("terms-require"));
            termsExclude = extractContentList(filters.getElementsByTagName("terms-exclude"));
            termsPromote = extractPromoteList(filters.getElementsByTagName("terms-promote"), "amount");

            if (searchSetIdentifier != null && !domainsInclude.isEmpty())
                throw new SearchFilterParserException("Search set identifier and domainLists can not both be specified");

            year = parseSpecificationLimit(filters.getElementsByTagName("year"), "year");
            size = parseSpecificationLimit(filters.getElementsByTagName("size"), "size");
            quality = parseSpecificationLimit(filters.getElementsByTagName("quality"), "quality");

            return new SearchFilterSpec(
                    userId,
                    identifier,
                    domainsInclude,
                    domainsExclude,
                    domainsPromote,
                    searchSetIdentifier,
                    termsRequire,
                    termsExclude,
                    termsPromote,
                    year,
                    size,
                    quality
            );
        }
        catch (ParserConfigurationException | IOException | SAXException e) {
            throw new SearchFilterParserException("Technical parser error", e);
        }
    }

    private static List<String> extractContentList(NodeList nodeList) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            String tagsList = nodeList.item(i).getTextContent();

            for (String item : tagsList.split("\\s+")) {
                if (item.isBlank()) continue;
                ret.add(item.toLowerCase());
            }
        }
        return ret;
    }

    private static List<Map.Entry<String, Float>> extractPromoteList(NodeList nodeList, String attrName) throws SearchFilterParserException {
        List<Map.Entry<String, Float>> ret = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {

            Element item = (Element) nodeList.item(i);

            if (!item.hasAttribute(attrName)) {
                throw new SearchFilterParserException("Element " + item.getTagName() + " missing attribute " + attrName);
            }

            float amount;
            try {
                amount = Float.parseFloat(item.getAttribute(attrName));
            }
            catch (NumberFormatException ex) {
                throw new SearchFilterParserException(
                        "Element " + item.getTagName() + "'s attribute "
                                + attrName + " failed to parse as a floating point number",
                        ex);
            }

            for (String entry : item.getTextContent().split("\\s+")) {
                if (entry.isBlank()) continue;
                ret.add(Map.entry(entry.toLowerCase(), amount));
            }
        }
        return ret;
    }

    private static SpecificationLimit parseSpecificationLimit(NodeList list, String name) throws SearchFilterParserException {
        if (list.getLength() == 0) return SpecificationLimit.none();
        if (list.getLength() > 1) throw new SearchFilterParserException("Multiple " + name + " tags!");
        var elem = (Element) list.item(0);

        String type = elem.getAttribute("type");

        String valueStr = elem.getAttribute("value");
        if (valueStr.isBlank())
            throw new SearchFilterParserException("Specification limit " + name + "is missing a type attribute");

        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException ex) {
            throw new SearchFilterParserException("Specification limit " + name + " has an invalid value (should be an integer)", ex);
        }

        return switch (type) {
            case "lt" -> SpecificationLimit.lessThan(value);
            case "gt" -> SpecificationLimit.greaterThan(value);
            case "eq" -> SpecificationLimit.equals(value);
            default -> throw new SearchFilterParserException("Specification limit " + name + " has missing or invalid 'type' attribute (should be 'lt', 'eq', or 'gt')");
        };
    }


}
