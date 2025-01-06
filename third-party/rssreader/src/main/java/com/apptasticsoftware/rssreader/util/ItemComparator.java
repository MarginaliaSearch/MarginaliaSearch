/*
 * MIT License
 *
 * Copyright (c) 2022, Apptastic Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.apptasticsoftware.rssreader.util;

import com.apptasticsoftware.rssreader.Channel;
import com.apptasticsoftware.rssreader.DateTimeParser;
import com.apptasticsoftware.rssreader.Item;

import java.util.Comparator;
import java.util.Objects;

/**
 * Provides different comparators for sorting item objects.
 */
@SuppressWarnings("java:S1133")
public final class ItemComparator {
    private static final String MUST_NOT_BE_NULL_MESSAGE = "Date time parser must not be null";

    private ItemComparator() {

    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @return comparator
     *
     * @deprecated As of release 3.9.0, replaced by {@link #oldestPublishedItemFirst()}
     */
    @Deprecated(since = "3.9.0", forRemoval = true)
    public static <I extends Item> Comparator<I> oldestItemFirst() {
        return oldestPublishedItemFirst();
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @return comparator
     */
    public static <I extends Item> Comparator<I> oldestPublishedItemFirst() {
        return Comparator.comparing((I i) ->
                        i.getPubDateZonedDateTime().orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Comparator for sorting Items on updated date if exist otherwise on publication date in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @return comparator
     */
    public static <I extends Item> Comparator<I> oldestUpdatedItemFirst() {
        return Comparator.comparing((I i) ->
                        i.getUpdatedZonedDateTime().orElse(i.getPubDateZonedDateTime().orElse(null)),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     *
     * @deprecated As of release 3.9.0, replaced by {@link #oldestPublishedItemFirst(DateTimeParser)}
     */
    @Deprecated(since = "3.9.0", forRemoval = true)
    public static <I extends Item> Comparator<I> oldestItemFirst(DateTimeParser dateTimeParser) {
        return oldestPublishedItemFirst(dateTimeParser);
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     */
    public static <I extends Item> Comparator<I> oldestPublishedItemFirst(DateTimeParser dateTimeParser) {
        Objects.requireNonNull(dateTimeParser, MUST_NOT_BE_NULL_MESSAGE);
        return Comparator.comparing((I i) ->
                        i.getPubDate().map(dateTimeParser::parse).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Comparator for sorting Items on updated date if exist otherwise on publication date in ascending order (oldest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     */
    public static <I extends Item> Comparator<I> oldestUpdatedItemFirst(DateTimeParser dateTimeParser) {
        Objects.requireNonNull(dateTimeParser, MUST_NOT_BE_NULL_MESSAGE);
        return Comparator.comparing((I i) ->
                        i.getUpdated().or(i::getPubDate).map(dateTimeParser::parse).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in descending order (newest first)
     * @param <I> any class that extends Item
     * @return comparator
     *
     * @deprecated As of release 3.9.0, replaced by {@link #newestPublishedItemFirst()}
     */
    @Deprecated(since = "3.9.0", forRemoval = true)
    public static <I extends Item> Comparator<I> newestItemFirst() {
        return newestPublishedItemFirst();
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in descending order (newest first)
     * @param <I> any class that extends Item
     * @return comparator
     */
    public static <I extends Item> Comparator<I> newestPublishedItemFirst() {
        return Comparator.comparing((I i) ->
                        i.getPubDateZonedDateTime().orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    /**
     * Comparator for sorting Items on updated date if exist otherwise on publication date in descending order (newest first)
     * @param <I> any class that extends Item
     * @return comparator
     */
    public static <I extends Item> Comparator<I> newestUpdatedItemFirst() {
        return Comparator.comparing((I i) ->
                        i.getUpdatedZonedDateTime().orElse(i.getPubDateZonedDateTime().orElse(null)),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in descending order (newest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     *
     * @deprecated As of release 3.9.0, replaced by {@link #newestPublishedItemFirst(DateTimeParser)}
     */
    @Deprecated(since = "3.9.0", forRemoval = true)
    public static <I extends Item> Comparator<I> newestItemFirst(DateTimeParser dateTimeParser) {
        return newestPublishedItemFirst(dateTimeParser);
    }

    /**
     * Comparator for sorting Items on initial creation or first availability (publication date) in descending order (newest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     */
    public static <I extends Item> Comparator<I> newestPublishedItemFirst(DateTimeParser dateTimeParser) {
        Objects.requireNonNull(dateTimeParser, MUST_NOT_BE_NULL_MESSAGE);
        return Comparator.comparing((I i) ->
                        i.getPubDate().map(dateTimeParser::parse).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    /**
     * Comparator for sorting Items on updated date if exist otherwise on publication date in descending order (newest first)
     * @param <I> any class that extends Item
     * @param dateTimeParser date time parser
     * @return comparator
     */
    public static <I extends Item> Comparator<I> newestUpdatedItemFirst(DateTimeParser dateTimeParser) {
        Objects.requireNonNull(dateTimeParser, MUST_NOT_BE_NULL_MESSAGE);
        return Comparator.comparing((I i) ->
                        i.getUpdated().or(i::getPubDate).map(dateTimeParser::parse).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())).reversed();
    }

    /**
     * Comparator for sorting Items on channel title
     * @param <I> any class that extends Item
     * @return comparator
     */
    public static <I extends Item> Comparator<I> channelTitle() {
        return Comparator.comparing(
                Item::getChannel,
                Comparator.nullsFirst(Comparator.comparing(
                        Channel::getTitle, Comparator.nullsFirst(Comparator.naturalOrder()))));
    }

}