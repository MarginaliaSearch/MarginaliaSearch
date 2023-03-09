package nu.marginalia.model.crawl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record PubDate(String dateIso8601, int year) {

    // First year we'll believe something can have been published on the web
    // cut off at 1995 to reduce false positive error rate; number of bona fide
    // documents from these years are so few almost all hits are wrong

    public static final int MIN_YEAR = 1995;

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

    private static final int ENCODING_OFFSET = MIN_YEAR + 1;

    public int yearByte() {
        if (hasYear()) {
            return year - ENCODING_OFFSET;
        }
        else return 0;
    }

    public static int fromYearByte(int yearByte) {
        return yearByte + ENCODING_OFFSET;
    }
    public static int toYearByte(int year) {
        return Math.max(0, year - ENCODING_OFFSET);
    }

}
