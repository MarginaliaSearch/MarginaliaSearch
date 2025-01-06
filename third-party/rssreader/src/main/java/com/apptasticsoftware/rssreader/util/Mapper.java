package com.apptasticsoftware.rssreader.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides methods for mapping field
 */
public final class Mapper {
    private static final Logger LOGGER = Logger.getLogger("com.apptasticsoftware.rssreader.util");

    private Mapper() { }

    /**
     * Maps a boolean text value (true, false, no or yes) to a boolean field. Text value can be in any casing.
     * @param text text value
     * @param func boolean setter method
     */
    public static void mapBoolean(String text, Consumer<Boolean> func) {
        text = text.toLowerCase();
        if ("true".equals(text) || "yes".equals(text)) {
            func.accept(Boolean.TRUE);
        } else if ("false".equals(text) || "no".equals(text)) {
            func.accept(Boolean.FALSE);
        }
    }

    /**
     * Maps a integer text value to a integer field.
     * @param text text value
     * @param func integer setter method
     */
    public static void mapInteger(String text, Consumer<Integer> func) {
        mapNumber(text, func, Integer::valueOf);
    }

    /**
     * Maps a long text value to a long field.
     * @param text text value
     * @param func long setter method
     */
    public static void mapLong(String text, Consumer<Long> func) {
        mapNumber(text, func, Long::valueOf);
    }

    private static <T> void mapNumber(String text, Consumer<T> func, Function<String, T> convert) {
        if (!isNullOrEmpty(text)) {
            try {
                func.accept(convert.apply(text));
            } catch (NumberFormatException e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, () -> String.format("Failed to convert %s. Message: %s", text, e.getMessage()));
                }
            }
        }
    }

    /**
     * Map value if field has not been mapped before
     * @param text value to map
     * @param getter getter to check if field is empty
     * @param setter setter to set value
     * @param <T> type
     */
    public static <T> void mapIfEmpty(String text, Supplier<T> getter, Consumer<String> setter) {
        if (isNullOrEmpty(getter) && !isNullOrEmpty(text)) {
            setter.accept(text);
        }
    }

    /**
     * Create a new instance if a getter returns optional empty and assigns the field the new instance.
     * @param getter getter method
     * @param setter setter method
     * @param factory factory for creating a new instance if field is not set before
     * @return existing or new instance
     * @param <T> any class
     */
    public static <T> T createIfNull(Supplier<Optional<T>> getter, Consumer<T> setter, Supplier<T> factory) {
        return createIfNullOptional(getter, setter, factory).orElse(null);
    }

    /**
     * Create a new instance if a getter returns optional empty and assigns the field the new instance.
     * @param getter getter method
     * @param setter setter method
     * @param factory factory for creating a new instance if field is not set before
     * @return existing or new instance
     * @param <T> any class
     */
    public static <T> Optional<T> createIfNullOptional(Supplier<Optional<T>> getter, Consumer<T> setter, Supplier<T> factory) {
        Optional<T> instance = getter.get();
        if (instance.isEmpty()) {
            T newInstance = factory.get();
            setter.accept(newInstance);
            instance = Optional.of(newInstance);
        }
        return instance;
    }

    private static <T> boolean isNullOrEmpty(Supplier<T> getter) {
        return getter.get() == null ||
                "".equals(getter.get()) ||
                getter.get() == Optional.empty() ||
                getter.get() instanceof Optional<?> &&
                        ((Optional<?>) getter.get())
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .map(String::isBlank)
                                .orElse(false);
    }

    private static boolean isNullOrEmpty(String text) {
        return text == null || text.isBlank();
    }
}
