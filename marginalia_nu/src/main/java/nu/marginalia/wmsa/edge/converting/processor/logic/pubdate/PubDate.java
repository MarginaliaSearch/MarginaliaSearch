package nu.marginalia.wmsa.edge.converting.processor.logic.pubdate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record PubDate(String dateIso8601, int year) {

    // First year we'll believe something can have been published on the web
    // ... Tim Berners Lee's recipe collection or something
    public static final int MIN_YEAR = 1989;

    // Last year we'll believe something can be published in
    public static final int MAX_YEAR = LocalDate.now().getYear() + 1;


    public PubDate() {
        this(null, Integer.MIN_VALUE);
    }

    public PubDate(LocalDate date) {
        this(date.format(DateTimeFormatter.ISO_DATE), date.getYear());
    }

    public boolean isEmpty() {
        return year == Integer.MIN_VALUE;
    }

    public String describe() {
        if (dateIso8601 != null)
            return dateIso8601;

        if (hasYear())
            return Integer.toString(year);

        return "";
    }

    public static boolean isValidYear(int year) {
        return year >= MIN_YEAR && year <= MAX_YEAR;
    }
    public boolean hasYear() {
        return isValidYear(this.year);
    }
}
