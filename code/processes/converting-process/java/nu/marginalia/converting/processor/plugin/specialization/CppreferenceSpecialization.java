package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class CppreferenceSpecialization extends WikiSpecialization {

    @Inject
    public CppreferenceSpecialization(SummaryExtractor summaryExtractor, TitleExtractor titleExtractor) {
        super(summaryExtractor, titleExtractor);
    }

    @Override
    public Document prune(Document original) {
        var doc = original.clone();

        doc.getElementsByClass("t-nv").remove();
        doc.getElementsByClass("toc").remove();
        doc.getElementsByClass("mw-head").remove();
        doc.getElementsByClass("printfooter").remove();
        doc.getElementsByClass("cpp-footer-base").remove();

        doc.title(doc.title() + " " + Strings.join(extractExtraTokens(doc.title()), ' '));

        return doc;
    }

    @Override
    public String getSummary(Document doc, Set<String> importantWords) {

        Element declTable = doc.getElementsByClass("t-dcl-begin").first();
        if (declTable != null) {
            var nextPar =  declTable.nextElementSibling();
            if (nextPar != null) {
                return nextPar.text();
            }
        }

        return super.getSummary(doc, importantWords);
    }


    public List<String> extractExtraTokens(String title) {

        if (!title.contains("::")) {
            return List.of();
        }
        if (!title.contains("-")) {
            return List.of();
        }

        title = StringUtils.split(title, '-')[0];

        String name = title;
        for (;;) {
            int lbidx = name.indexOf('<');
            int rbidx = name.indexOf('>');

            if (lbidx > 0 && rbidx > lbidx) {
                String className = name.substring(0, lbidx);
                String methodName = name.substring(rbidx + 1);
                name = className + methodName;
            } else {
                break;
            }
        }


        List<String> tokens = new ArrayList<>();

        for (var part : name.split("\\s*,\\s*")) {
            if (part.endsWith(")") && !part.endsWith("()")) {
                int parenStart = part.indexOf('(');
                if (parenStart > 0) { // foo(...) -> foo
                    part = part.substring(0, parenStart);
                }
                else if (parenStart == 0) { // (foo) -> foo
                    part = part.substring(1, part.length() - 1);
                }
            }

            part = part.trim();
            if (part.contains("::")) {
                tokens.add(part);
                if (part.startsWith("std::")) {
                    tokens.add(part.substring(5));

                    int ss = part.indexOf("::", 5);
                    if (ss > 0) {
                        tokens.add(part.substring(0, ss));
                        tokens.add(part.substring(ss+2));
                    }

                }
            }
        }

        return tokens;
    }


}
