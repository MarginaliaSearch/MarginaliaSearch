package nu.marginalia.wmsa.edge.converting.processor.logic.topic;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AdblockSimulator {

    List<String> idRules = new ArrayList();
    List<String> classRules = new ArrayList();
    List<Predicate<String>> scriptRules = new ArrayList();

    public AdblockSimulator(Path adsDefinition) throws IOException  {
        try (var lineStream = Files.lines(adsDefinition)) {
            lineStream.skip(1).forEach(this::addRule);
        }
    }

    private void addRule(String s) {
        if (s.startsWith("##") && !s.contains(":")) {
            if (s.startsWith("###")) {
                idRules.add(s.substring(3));
            } else if(s.startsWith("##.")) {
                classRules.add(s.substring(3));
            }
        }
        else if (!s.startsWith("!") && !s.contains("#")){
            scriptRules.add(toRegexMatcher(s));
        }
    }

    private Predicate<String> toRegexMatcher(String s) {

        System.out.println("<-" + s);

        s = s.replaceAll("\\?", "\\\\?");
        s = s.replaceAll("\\.", "\\\\.");
        s = s.replaceAll("\\$", "\\\\\\$");

        if (s.startsWith("||")) {
            s = s.replaceFirst("\\|\\|","^http(s)?://");
        }

        s = s.replaceAll("\\|", "\\\\|");
        s = s.replaceAll("\\*", ".*");
        s = s.replaceAll("\\^", "[?/]");


        System.out.println("->" + s);
        return Pattern.compile(s).asPredicate();
    }

    class RuleVisitor implements NodeFilter {
        public boolean sawAds;
        Pattern spPattern = Pattern.compile("\\s");

        @Override
        public FilterResult head(Node node, int depth) {

            if (node.attributesSize() > 0 && node instanceof Element elem) { // instanceof is slow

                String id = elem.id();
                for (var rule : idRules) {
                    if (rule.equals(id)) {
                        sawAds = true;
                        return FilterResult.STOP;
                    }
                }

                String classes = elem.className();
                if (classes.isBlank()) return FilterResult.CONTINUE;

                if (classes.indexOf(' ') > 0) {
                    String[] classNames = spPattern.split(classes);
                    for (var rule : classRules) {

                        for (var className : classNames) {
                            if (className.equals(rule)) {
                                sawAds = true;
                                return FilterResult.STOP;
                            }
                        }
                    }
                }
                else { // tag only has one class
                    for (var rule : classRules) {
                        if (classes.equals(rule)) {
                            sawAds = true;
                            return FilterResult.STOP;
                        }
                    }
                }

                if ("script".equals(elem.tagName())) {
                    String src = elem.attr("src");

                    for (var rule : scriptRules) {
                        if (rule.test(src)) {
                            sawAds = true;
                            return FilterResult.STOP;
                        }
                    }
                }

                return FilterResult.CONTINUE;
            }
            return FilterResult.CONTINUE;
        }

        @Override
        public FilterResult tail(Node node, int depth) {
            return FilterResult.CONTINUE;
        }
    }

    public boolean hasAds(Document document) {

        RuleVisitor ruleVisitor = new RuleVisitor();
        document.filter(ruleVisitor);

        return ruleVisitor.sawAds;
    }

}
