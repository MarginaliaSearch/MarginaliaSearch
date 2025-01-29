package nu.marginalia.converting.processor.classifier.adblock;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.WmsaHome;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class AdblockSimulator {
    private final Set<String> idRules = new HashSet<>();

    private final Set<String> classRules = new HashSet<>();
    private final List<Predicate<String>> scriptRules = new ArrayList<>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public AdblockSimulator() throws IOException  {
        Path adDef = WmsaHome.getAdsDefinition();

        if (!Files.exists(adDef)) {
            logger.error("Can not find ads definition file in {}", adDef);
            return;
        }

        try (var lineStream = Files.lines(adDef)) {
            lineStream.skip(1).forEach(this::addRule);
        }
    }

    public boolean hasAds(Document document) {
        return false;
        /* Disabled for now, it doesn't work very well and it's very slow.

        RuleVisitor ruleVisitor = new RuleVisitor();

        document.filter(ruleVisitor);

        return ruleVisitor.sawAds; */
    }

    private void addRule(String s) {
        try {
            if (s.startsWith("##") && !s.contains(":")) {
                if (s.startsWith("###")) {
                    idRules.add(s.substring(3));
                } else if (s.startsWith("##.")) {
                    classRules.add(s.substring(3));
                }
            } else if (s.startsWith("/^")) {
                int end = s.indexOf("[^\\]/");
                if (end >= 0) {
                    String patternString = s.substring(1, end+1);
                    scriptRules.add(Pattern.compile(patternString).asPredicate());
                }
            } else if (!s.startsWith("!") && !s.contains("#") && !s.startsWith("@@")) {
                if (!s.contains("$")) {
                    scriptRules.add(toRegexMatcher(s));
                }
                else if (s.contains("$script") && !s.contains("domain=")) {
                    scriptRules.add(toRegexMatcher(s.substring(0, s.indexOf('$'))));
                }
            }
        }
        catch (Exception ex) {
            System.err.println("Failed to add rule " + s);
        }
    }

    private Predicate<String> toRegexMatcher(String s) {
        String sOriginal = s;
        if (s.isBlank()) return unused -> false;

        // In some cases, regexes aren't necessary
        if (s.matches("[&?=/A-Za-z0-9._-]+")) {
            if (s.startsWith("/")) {
                return str -> str.equals(sOriginal);
            }
            else {
                return str -> str.contains(sOriginal);
            }
        }
        if (s.matches("[&?=/A-Za-z0-9._-]+\\*")) {
            return str -> str.startsWith(sOriginal.substring(0, sOriginal.length()-1));
        }

        String s0 = s;
        s = s.replaceAll("\\?", "\\\\?");
        s = s.replaceAll("\\.", "\\\\.");

        s = s.replaceAll("\\^", "[?/]");
        s = s.replaceAll("\\*", ".*");

        if (s.startsWith("||")) {
            s = s.replaceFirst("\\|\\|","^http[s]?://.*");
        }

        s = s.replaceAll("\\|", "\\\\|");
        return Pattern.compile(s).asPredicate();
    }


    // Refrain from cleaning up this code, it's very hot code and needs to be fast.
    // This version is about 100x faster than a "clean" first stab implementation.

    class RuleVisitor implements NodeFilter {
        public boolean sawAds;

        Pattern spPattern = Pattern.compile("\\s");

        @Override
        public FilterResult head(Node node, int depth) {

            if (node.attributesSize() > 0 && node instanceof Element elem) {
                if (testId(elem) || testClass(elem) || testScriptTags(elem)) {
                    sawAds = true;
                    return FilterResult.STOP;
                }
            }
            return FilterResult.CONTINUE;
        }

        private boolean testScriptTags(Element elem) {
            if (!"script".equals(elem.tagName())) {
                return false;
            }

            String src = elem.attr("src");
            for (var rule : scriptRules) {
                if (rule.test(src)) {
                    return true;
                }
            }

            return false;
        }

        private boolean testId(Element elem) {
            String id = elem.id();

            return idRules.contains(id);
        }

        private boolean testClass(Element elem) {
            String classes = elem.className();
            if (classes.isBlank())
                return false;

            if (classes.indexOf(' ') > 0) {
                String[] classNames = spPattern.split(classes);
                for (var className : classNames) {
                    if (classRules.contains(className))
                        return true;
                }
            }
            else { // tag only has one class, no need to split
                return classRules.contains(classes);
            }

            return false;
        }
        @Override
        public FilterResult tail(Node node, int depth) {
            return FilterResult.CONTINUE;
        }

    }

}
