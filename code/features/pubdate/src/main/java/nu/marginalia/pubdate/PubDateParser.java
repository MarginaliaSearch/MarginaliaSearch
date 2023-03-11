package nu.marginalia.pubdate;

import nu.marginalia.crawling.common.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class PubDateParser {

    public static Optional<PubDate> attemptParseDate(String date) {
        return Optional.ofNullable(date)
                .filter(str -> str.length() >= 4 && str.length() < 32)
                .flatMap(str ->
                        parse8601(str)
                                .or(() -> parse1123(str))
                                .or(() -> dateFromHighestYearLookingSubstring(str))
                )
                .filter(PubDateParser::validateDate);
    }

    public static OptionalInt parseYearString(String yearString) {
        try {
            return OptionalInt.of(Integer.parseInt(yearString));
        }
        catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }


    private static final Pattern yearPattern = Pattern.compile("\\d{4}");

    public static Optional<PubDate> dateFromHighestYearLookingSubstring(String maybe) {
        var matcher = yearPattern.matcher(maybe);

        int min = PubDate.MAX_YEAR + 1;
        int max = PubDate.MIN_YEAR - 1;

        for (int i = 0; i < maybe.length() && matcher.find(i); i = matcher.end()) {

            String segment = maybe.substring(matcher.start(), matcher.end());
            OptionalInt year = parseYearString(segment);

            if (year.isEmpty())
                continue;

            int y = year.getAsInt();
            if (PubDate.isValidYear(y)) {
                if (max < y) max = y;
                if (min > y) min = y;
            }
        }

        if (max != min && PubDate.isValidYear(min) && PubDate.isValidYear(max)) {
            return Optional.of(new PubDate(null, guessYear(min, max)));
        }

        if (max >= PubDate.MIN_YEAR)
            return Optional.of(new PubDate(null, max));
        else
            return Optional.empty();
    }


    public static Optional<PubDate> dateFromHighestYearLookingSubstringWithGuess(String maybe, int guess) {
        var matcher = yearPattern.matcher(maybe);

        int min = PubDate.MAX_YEAR + 1;
        int max = PubDate.MIN_YEAR - 1;

        for (int i = 0; i < maybe.length() && matcher.find(i); i = matcher.end()) {

            String segment = maybe.substring(matcher.start(), matcher.end());
            OptionalInt year = parseYearString(segment);

            if (year.isEmpty())
                continue;

            int y = year.getAsInt();
            if (PubDate.isValidYear(y)) {
                if (max < y) max = y;
                if (min > y) min = y;
            }
        }

        if (max != min && PubDate.isValidYear(min) && PubDate.isValidYear(max)) {
            return Optional.of(new PubDate(null, guessYear(min, max, guess)));
        }

        if (max >= PubDate.MIN_YEAR)
            return Optional.of(new PubDate(null, max));
        else
            return Optional.empty();
    }

    public static int guessYear(int min, int max, int educatedGuess) {
        int var = max - min;

        if (var < 3)
            return min;

        int avg = (max + min) / 2;
        int guess = (avg + educatedGuess) / 2;

        if (guess < min)
            return min;
        if (guess > max)
            return max;

        return guess;
    }

    public static int guessYear(int min, int max) {
        return (max + min) / 2;
    }

    public static int guessYear(HtmlStandard standard) {
        // Create some jitter to avoid having documents piling up in the same four years
        // as this would make searching in those years disproportionately useless

        double guess = standard.yearGuess + ThreadLocalRandom.current().nextGaussian();

        if (guess < PubDate.MIN_YEAR) {
            return PubDate.MIN_YEAR;
        }
        if (guess > PubDate.MAX_YEAR) {
            return PubDate.MAX_YEAR;
        }
        return (int) guess;
    }

    public static Optional<PubDate> parse8601(String maybe) {
        return parseOptionally(maybe, DateTimeFormatter.ISO_DATE)
                .or(() -> parseOptionallyWithTime(maybe, DateTimeFormatter.ISO_DATE_TIME))
                .or(() -> parseOptionallyWithZonedTime(maybe, DateTimeFormatter.ISO_DATE_TIME))
                .map(PubDate::new);
    }

    public static Optional<PubDate> parse1123(String maybe) {
        return parseOptionally(maybe, DateTimeFormatter.RFC_1123_DATE_TIME)
                .map(PubDate::new);
    }

    public static Optional<LocalDate> parseOptionally(String str, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDate.parse(str, formatter));
        }
        catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
    public static Optional<LocalDate> parseOptionallyWithTime(String str, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDateTime.parse(str, formatter).toLocalDate());
        }
        catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
    public static Optional<LocalDate> parseOptionallyWithZonedTime(String str, DateTimeFormatter formatter) {
        try {
            return Optional.of(ZonedDateTime.parse(str, formatter).toLocalDate());
        }
        catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
    public static boolean validateDate(PubDate date) {
        return (date.year() >= PubDate.MIN_YEAR && date.year() <= PubDate.MAX_YEAR);
    }
}
