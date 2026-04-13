package nu.marginalia.model.crawl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.annotation.Nullable;

/** Publication date of a document with two levels of accuracy */
public sealed interface PubDate permits PubDate.ExactDate, PubDate.ApproximateYear {

    /** Bounds for yearByte encoding */
    int MIN_YEAR = 1995;
    int MAX_YEAR = LocalDate.now().getYear() + 1;

    /** March 12, 1989. Origin for the dateShort encoding. */
    LocalDate WEB_EPOCH = LocalDate.of(1989, 3, 12);
    public int INVALID_DATE_SENTINEL = 0;

    int year();
    boolean hasYear();
    boolean isEmpty();
    String describe();

    @Nullable
    String dateIso8601();

    /** Days since WEB_EPOCH, or 0 if only year-level accuracy. */
    int dateShort();

    int yearByte();

    static PubDate ofDate(LocalDate date) {
        return new ExactDate(date);
    }

    static PubDate ofYear(int year) {
        return new ApproximateYear(year);
    }

    static PubDate unknown() {
        return new ApproximateYear(Integer.MIN_VALUE);
    }

    static boolean isValidYear(int year) {
        return year >= MIN_YEAR && year <= MAX_YEAR;
    }

    int BYTE_ENCODING_OFFSET = MIN_YEAR + 1;

    static int fromYearByte(int yearByte) {
        return yearByte + BYTE_ENCODING_OFFSET;
    }

    static int toYearByte(int year) {
        return Math.max(0, year - BYTE_ENCODING_OFFSET);
    }

    static int toDateShort(LocalDate date) {
        long days = ChronoUnit.DAYS.between(WEB_EPOCH, date);
        return (int) Math.max(1, Math.min(Short.MAX_VALUE, days));
    }

    @Nullable
    static LocalDate fromDateShort(int ds) {
        if (ds <= 0) return null;
        return WEB_EPOCH.plusDays(ds);
    }

    record ExactDate(LocalDate date) implements PubDate {
        @Override public int year() { return date.getYear(); }
        @Override public boolean hasYear() { return isValidYear(year()); }
        @Override public boolean isEmpty() { return false; }
        @Override public String dateIso8601() { return date.format(DateTimeFormatter.ISO_DATE); }
        @Override public int dateShort() { return toDateShort(date); }
        @Override public int yearByte() { return hasYear() ? year() - BYTE_ENCODING_OFFSET : 0; }
        @Override public String describe() { return dateIso8601(); }
    }

    record ApproximateYear(int year) implements PubDate {
        @Override public boolean hasYear() { return isValidYear(year); }
        @Override public boolean isEmpty() { return !hasYear(); }
        @Override public @Nullable String dateIso8601() { return null; }
        @Override public int dateShort() { return 0; }
        @Override public int yearByte() { return hasYear() ? year - BYTE_ENCODING_OFFSET : 0; }
        @Override public String describe() { return hasYear() ? Integer.toString(year) : ""; }
    }
}
