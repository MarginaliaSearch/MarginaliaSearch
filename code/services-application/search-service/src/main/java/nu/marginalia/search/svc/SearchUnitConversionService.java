package nu.marginalia.search.svc;

import nu.marginalia.assistant.client.AssistantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class SearchUnitConversionService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Pattern conversionPattern = Pattern.compile("((\\d+|\\s+|[.()\\-^+%*/]|log[^a-z]|log2[^a-z]|sqrt[^a-z]|log10|cos[^a-z]|sin[^a-z]|tan[^a-z]|log2|pi[^a-z]|e[^a-z]|2pi[^a-z])+)\\s*([a-zA-Z][a-zA-Z^.0-9]*\\s?[a-zA-Z^.0-9]*)\\s+in\\s+([a-zA-Z^.0-9]+\\s?[a-zA-Z^.0-9]*)");
    private final Predicate<String> evalPredicate = Pattern.compile("(\\d+|\\s+|[.()\\-^+%*/]|log|log2|sqrt|log10|cos|sin|tan|pi|e|2pi)+").asMatchPredicate();

    private final AssistantClient assistantClient;

    @Inject
    public SearchUnitConversionService(AssistantClient assistantClient) {
        this.assistantClient = assistantClient;
    }

    public Optional<String> tryConversion(String query) {
        var matcher = conversionPattern.matcher(query);
        if (!matcher.matches())
            return Optional.empty();

        String value = matcher.group(1);
        String from = matcher.group(3);
        String to = matcher.group(4);

        logger.info("{} -> '{}' '{}' '{}'", query, value, from, to);

        try {
            var resultFuture = assistantClient.unitConversion(value, from, to);
            return Optional.of(
                    resultFuture.get(100, TimeUnit.MILLISECONDS)
            );
        } catch (ExecutionException e) {
            logger.error("Error in unit conversion", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for unit conversion", e);
        } catch (TimeoutException e) {
            // Ignore
        }
        return Optional.empty();
    }


    public @CheckForNull Future<String> tryEval(String query) {
        if (!evalPredicate.test(query)) {
            return null;
        }

        var expr = query.toLowerCase().trim();

        if (expr.chars().allMatch(Character::isDigit)) {
            return null;
        }

        logger.info("eval({})", expr);

        return assistantClient.evalMath(expr);
    }
}
