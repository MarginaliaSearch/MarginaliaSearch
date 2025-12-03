package nu.marginalia.search.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.*;
import io.jooby.annotation.GET;
import io.jooby.annotation.POST;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterParser;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.search.model.NavbarModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class SearchFilterService {
    private static final Logger log = LoggerFactory.getLogger(SearchFilterService.class);
    private final Gson gson = GsonFactory.get();

    private final SearchFilterParser filterParser = new SearchFilterParser();
    private final WebsiteUrl websiteUrl;

    @Inject
    public SearchFilterService(WebsiteUrl websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    @GET("/filters")
    public ModelAndView<?> filterViewGET(Context context) {
        SearchFilterSpec currentFilter = SearchFilterSpec.defaultForUser("WEB", "ADHOC");

        return new MapModelAndView("filter/loader.jte",
                Map.of("navbar", NavbarModel.LIMBO,
                        "problems", List.of(),
                        "filter", currentFilter)
        );
    }

    @POST("/filters")
    public ModelAndView<?> filterViewPOST(Context context) {
        Body body = context.body();
        List<String> problems = new ArrayList<>();
        SearchFilterSpec currentFilter;

        log.info("Recovered cookie");
        try {
            currentFilter = filterParser.parse("WEB", "ADHOC", body.value());
        }
        catch (SearchFilterParser.SearchFilterParserException e) {
            problems.add("Problem parsing existing filter specification: " + e);
            currentFilter = SearchFilterSpec.defaultForUser("WEB", "ADHOC");
        }
        catch (Exception e) {
            log.error("Error parsing filter spec", e);

            problems.add("Internal error parsing existing filter specification");
            currentFilter = SearchFilterSpec.defaultForUser("WEB", "ADHOC");
        }

        return new MapModelAndView("filter/main.jte",
                Map.of("navbar", NavbarModel.LIMBO,
                        "problems", problems,
                        "filter", currentFilter)
        );
    }

    @POST("/filters/export")
    public ModelAndView<?> exportFilter(Context context) {
        return new MapModelAndView("filter/main.jte",
                Map.of("navbar", NavbarModel.LIMBO)
        );
    }

    record JsonModel(
            List<String> termsRequired,
            List<String> termsExcluded,
            List<String> termsPriority,
            List<Float> termsPriorityWeights,
            List<String> domainsRequired,
            List<String> domainsExcluded,
            List<String> domainsPriority,
            List<Float> domainsPriorityWeights,
            String temporalBias,
            String yearLimitType,
            String yearLimitValue,
            String sizeLimitType,
            String sizeLimitValue,
            String rankLimitType,
            String rankLimitValue,
            String qualityLimitType,
            String qualityLimitValue
    ) {

    }

    // map to @POST("/filters/format")
    public String saveFilter(Context context) {
        String postBody = context.body().value();
        SearchFilterSpec spec;

        MediaType requestType = context.getRequestType();
        if (requestType == null) {
            context.setResponseCode(400);
            return null;
        }

        if (requestType.matches("text/xml")) {
            try {
                spec = filterParser.parse("WEB", "ADHOC", postBody);
            } catch (SearchFilterParser.SearchFilterParserException e) {
                context.setResponseCode(500);
                return e.getMessage();
            }
        }
        else if (requestType.matches("application/json")) {
            JsonModel model = gson.fromJson(postBody, JsonModel.class);

            List<Map.Entry<String, Float>> domainsPromote = new ArrayList<>(model.domainsPriority.size());
            for (int i = 0; i < model.domainsPriority().size(); i++) {
                domainsPromote.add(
                        Map.entry(model.domainsPriority.get(i), model.domainsPriorityWeights.get(i))
                );
            }
            List<Map.Entry<String, Float>> termsPromote = new ArrayList<>(model.termsPriority.size());
            for (int i = 0; i < model.termsPriority().size(); i++) {
                termsPromote.add(
                        Map.entry(model.termsPriority.get(i), model.termsPriorityWeights.get(i))
                );
            }

            spec = new SearchFilterSpec(
                    "WEB",
                    "ADHOC",
                    model.domainsRequired,
                    model.domainsExcluded,
                    domainsPromote,
                    "NONE",
                    model.termsRequired,
                    model.termsExcluded,
                    termsPromote,
                    parseSpecificationLimit(model.yearLimitType, model.yearLimitValue),
                    parseSpecificationLimit(model.sizeLimitType, model.sizeLimitValue),
                    parseSpecificationLimit(model.qualityLimitType, model.qualityLimitValue),
                    parseSpecificationLimit(model.rankLimitType, model.rankLimitValue),
                    model.temporalBias,
                    QueryStrategy.AUTO
            );
        }
        else {
            log.info("Got unexpected CT {}", requestType);
            context.setResponseCode(400);
            return "Set Content-Type to either text/xml or application/json";
        }

        String renderedXml = filterParser.renderToXml(spec);
        context.setResponseType("text/xml");

        return renderedXml;
    }

    private SpecificationLimit parseSpecificationLimit(String type, String valueStr) {

        if (valueStr == null || valueStr.isBlank())
            return SpecificationLimit.none();

        int value = Integer.parseInt(valueStr);

        return switch (type) {
            case "none" -> SpecificationLimit.none();
            case "equals" -> SpecificationLimit.equals(value);
            case "less than" -> SpecificationLimit.lessThan(value);
            case "greater than" -> SpecificationLimit.greaterThan(value);
            default -> throw new IllegalArgumentException("Bad type " + type);
        };
    }
}
