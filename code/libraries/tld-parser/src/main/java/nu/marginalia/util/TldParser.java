import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the public suffix list into a HashSet.
 * Provides utility functions for stripping TLDs from the domain.
 */
public class TldParser {

    /** Parsed list of TLDs from the publicly available list */
    public Set<String> tlds;

    /**
     * Construct TldParser by populating the hash set of tlds
     * 
     * @param tldPath path to location of public_suffix_list.dat
     */
    public TldParser(String tldPath) {
        try {
            __parsePublicSuffixFile(tldPath);
        } catch (IOException e) {
            // implement - what exception do we want to throw here?
        }
    }

    /**
     * Return the domain as a string without the tld, ie google.com -> google
     * 
     * @param domain the domain to strip the TLD from
     * @return domain without TLD as string
     */
    public String stripTld(String domain) {
        boolean match = true;

        // probably a pretty naive approach, but seems simple enough -
        // take the domain and look up each ending until one matches
        // ex: abc.bb.com , first look up bb.com, if that does not exist, look up com
        while (match) {
            String lookupDomain = domain.substring(domain.indexOf(".") + 1);
            if (tlds.contains(lookupDomain)) {
                return domain.replace("." + lookupDomain, "");
            }
            if (!lookupDomain.contains(".")) {
                match = false;
            }
        }
        return domain;
    }

    /**
     * Given a path to a valid TLD file following the format outlined in
     * https://publicsuffix.org/list/public_suffix_list.dat, return a
     * Set representation of all TLDs.
     * 
     * @throws FileNotFoundException
     */
    private void __parsePublicSuffixFile(String tldPath) throws IOException {
        this.tlds = new HashSet<String>();
        List<String> lines = Files.readAllLines(Paths.get(tldPath));

        for (String line : lines) {
            line = line.trim().toLowerCase();

            // We only want the line up to the first whitespace
            try {
                line = line.split(" ")[0];
            } catch (Exception e) {
                continue;
            }

            // Ignore all comments and "!"
            if (line.startsWith("//") || line.startsWith("!")) {
                continue;
            }
            this.tlds.add(line);
        }
    }
}