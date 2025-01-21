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
package com.apptasticsoftware.rssreader.module.itunes;

import com.apptasticsoftware.rssreader.Channel;
import com.apptasticsoftware.rssreader.DateTimeParser;

import java.util.*;

/**
 * Class representing the Itunes channel.
 */
public class ItunesChannel extends Channel {

    private String itunesImage;
    private final List<String> itunesCategories = new ArrayList<>();
    private boolean itunesExplicit;
    private String itunesAuthor;
    private ItunesOwner itunesOwner;
    private String itunesTitle;
    private String itunesSubtitle;
    private String itunesSummary;
    private String itunesType;
    private String itunesNewFeedUrl;
    private boolean itunesBlock;
    private boolean itunesComplete;

    /**
     * Constructor
     * @param dateTimeParser timestamp parser
     */
    public ItunesChannel(DateTimeParser dateTimeParser) {
        super(dateTimeParser);
    }

    /**
     * Get the artwork for the show.
     * Specify your show artwork by providing a URL linking to it.
     * @return image
     */
    public String getItunesImage() {
        return itunesImage;
    }

    /**
     * Set the artwork for the show.
     * Specify your show artwork by providing a URL linking to it.
     * @param image image
     */
    public void setItunesImage(String image) {
        this.itunesImage = image;
    }

    /**
     * Get the show category information. For a complete list of categories and subcategories
     * @return list of categories
     */
    public List<String> getItunesCategories() {
        return Collections.unmodifiableList(itunesCategories);
    }

    /**
     * Add the show category information. For a complete list of categories and subcategories
     * @param itunesCategory category
     */
    public void addItunesCategory(String itunesCategory) {
        if (itunesCategory != null) {
            itunesCategories.add(itunesCategory);
        }
    }

    /**
     * Get the podcast parental advisory information.
     * The explicit value can be one of the following:
     * <p>
     * True. If you specify true, indicating the presence of explicit content,
     * Apple Podcasts displays an Explicit parental advisory graphic for your podcast.
     * Podcasts containing explicit material aren’t available in some Apple Podcasts territories.
     * <p>
     * False. If you specify false, indicating that your podcast doesn’t contain
     * explicit language or adult content, Apple Podcasts displays a Clean parental
     * advisory graphic for your podcast.
     * @return explicit
     */
    public Boolean getItunesExplicit() {
        return itunesExplicit;
    }

    /**
     * Get the podcast parental advisory information.
     * The explicit value can be one of the following:
     * <p>
     * True. If you specify true, indicating the presence of explicit content,
     * Apple Podcasts displays an Explicit parental advisory graphic for your podcast.
     * Podcasts containing explicit material aren’t available in some Apple Podcasts territories.
     * <p>
     * False. If you specify false, indicating that your podcast doesn’t contain
     * explicit language or adult content, Apple Podcasts displays a Clean parental
     * advisory graphic for your podcast.
     * @param itunesExplicit explicit
     */
    public void setItunesExplicit(Boolean itunesExplicit) {
        this.itunesExplicit = itunesExplicit;
    }

    /**
     * Get the group responsible for creating the show.
     * @return author
     */
    public Optional<String> getItunesAuthor() {
        return Optional.ofNullable(itunesAuthor);
    }

    /**
     * Set the group responsible for creating the show.
     * @param itunesAuthor author
     */
    public void setItunesAuthor(String itunesAuthor) {
        this.itunesAuthor = itunesAuthor;
    }

    /**
     * Set the podcast owner contact information.
     * @param itunesOwner owner
     */
    public void setItunesOwner(ItunesOwner itunesOwner) {
        this.itunesOwner = itunesOwner;
    }

    /**
     * Get the podcast owner contact information.
     * @return owner
     */
    public Optional<ItunesOwner> getItunesOwner() {
        return Optional.ofNullable(itunesOwner);
    }

    /**
     * Get the title specific for Apple Podcasts.
     * @return title
     */
    public Optional<String> getItunesTitle() {
        return Optional.ofNullable(itunesTitle);
    }

    /**
     * Set the title specific for Apple Podcasts.
     * @param itunesTitle title
     */
    public void setItunesTitle(String itunesTitle) {
        this.itunesTitle = itunesTitle;
    }

    /**
     * Get the subtitle specific for Apple Podcasts.
     * @return subtitle
     */
    public Optional<String> getItunesSubtitle() {
        return Optional.ofNullable(itunesSubtitle);
    }

    /**
     * Set the subtitle specific for Apple Podcasts.
     * @param itunesSubtitle subtitle
     */
    public void setItunesSubtitle(String itunesSubtitle) {
        this.itunesSubtitle = itunesSubtitle;
    }

    /**
     * Get the summary.
     * @return summary
     */
    public String getItunesSummary() {
        return itunesSummary;
    }

    /**
     * Set the summary.
     * @param itunesSummary summary
     */
    public void setItunesSummary(String itunesSummary) {
        this.itunesSummary = itunesSummary;
    }

    /**
     * Get the type of show.
     * @return type
     */
    public Optional<String> getItunesType() {
        return Optional.ofNullable(itunesType);
    }

    /**
     * Set the type of show.
     * @param itunesType type
     */
    public void setItunesType(String itunesType) {
        this.itunesType = itunesType;
    }

    /**
     * Get the new podcast RSS Feed URL.
     * If you change the URL of your podcast feed, you should use this tag in your new feed.
     * @return new feed url
     */
    public Optional<String> getItunesNewFeedUrl() {
        return Optional.ofNullable(itunesNewFeedUrl);
    }

    /**
     * Set the new podcast RSS Feed URL.
     * If you change the URL of your podcast feed, you should use this tag in your new feed.
     * @param itunesNewFeedUrl new feed url
     */
    public void setItunesNewFeedUrl(String itunesNewFeedUrl) {
        this.itunesNewFeedUrl = itunesNewFeedUrl;
    }

    /**
     * Get the podcast show or hide status.
     * If you want your show removed from the Apple directory, use this tag.
     * @return block
     */
    public boolean isItunesBlock() {
        return itunesBlock;
    }

    /**
     * Set the podcast show or hide status.
     * If you want your show removed from the Apple directory, use this tag.
     * @param itunesBlock block
     */
    public void setItunesBlock(boolean itunesBlock) {
        this.itunesBlock = itunesBlock;
    }

    /**
     * Set the podcast update status.
     * If you will never publish another episode to your show, use this tag.
     * @return complete
     */
    public boolean isItunesComplete() {
        return itunesComplete;
    }

    /**
     * Get the podcast update status.
     * If you will never publish another episode to your show, use this tag.
     * @param itunesComplete complete
     */
    public void setItunesComplete(boolean itunesComplete) {
        this.itunesComplete = itunesComplete;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ItunesChannel that = (ItunesChannel) o;
        return getItunesExplicit() == that.getItunesExplicit() &&
                isItunesBlock() == that.isItunesBlock() &&
                isItunesComplete() == that.isItunesComplete() &&
                Objects.equals(getItunesImage(), that.getItunesImage()) &&
                getItunesCategories().equals(that.getItunesCategories()) &&
                Objects.equals(getItunesAuthor(), that.getItunesAuthor()) &&
                Objects.equals(getItunesOwner(), that.getItunesOwner()) &&
                Objects.equals(getItunesTitle(), that.getItunesTitle()) &&
                Objects.equals(getItunesSubtitle(), that.getItunesSubtitle()) &&
                Objects.equals(getItunesSummary(), that.getItunesSummary()) &&
                Objects.equals(getItunesType(), that.getItunesType()) &&
                Objects.equals(getItunesNewFeedUrl(), that.getItunesNewFeedUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getItunesImage(), getItunesCategories(), getItunesExplicit(),
                getItunesAuthor(), getItunesOwner(), getItunesTitle(), getItunesSubtitle(), getItunesSummary(),
                getItunesType(), getItunesNewFeedUrl(), isItunesBlock(), isItunesComplete());
    }
}
