import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the public suffix list into a HashSet.
 * Provides utility functions for stripping suffixes from the domain.
 */
public class TldParser {

    /** Parsed list of suffixes from the publicly available list */
    public final Set<String> domainSuffixes;

    /**
     * Construct TldParser by populating the hash set of suffixes
     * 
     * @param suffixFilePath path to location of public_suffix_list.dat
     * @throws IOException TODO - better error handling. works for now..
     */
    public TldParser(String suffixFilePath) throws IOException {
        this.domainSuffixes = __parsePublicSuffixFile(suffixFilePath);
    }

    /**
     * Return the top level domain (suffix) from a given domain
     * @param domain without protocol or www. 
     * @return the tld of the given domain
     */
    public String getTld(String domain) {
        
        String[] possibleTlds = domain.split("\\.");

        // Is it safe to assume that possibleTlds[0] is not a TLD?
        for (int i = 1; i < possibleTlds.length; i++) {
            if (this.domainSuffixes.contains(possibleTlds[i])) {
                return possibleTlds[i];
            }
        } 
        return domain;
    }

    /**
     * Return the domain as a string without the suffix, ie google.com -> google
     * 
     * @param domain the domain to strip the TLD from
     * @return domain without TLD as string
     */
    public String stripDomain(String domain) {
        
        boolean looking = true;
        while (looking) {
            String lookupDomain = domain.substring(domain.indexOf(".") + 1);
            if (domainSuffixes.contains(lookupDomain)) {
                return domain.replace("." + lookupDomain, "");
            }
            if (!lookupDomain.contains(".")) {
                looking = false;
            }
        }
        return domain;
    }

    /**
     * Given a path to a valid public suffix file following the format outlined in
     * https://publicsuffix.org/list/public_suffix_list.dat, return a
     * Set representation of all suffixes
     * 
     * @throws IOException
     */
    private static Set<String> __parsePublicSuffixFile(String tldPath) throws IOException {
        Set<String> suffixes = new HashSet<String>();
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
            suffixes.add(line);
        }
        return suffixes;
    }
}