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
package com.apptasticsoftware.rssreader;

import com.apptasticsoftware.rssreader.util.Default;
import com.apptasticsoftware.rssreader.util.Util;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Class representing the RSS channel.
 */
public class Channel {
    private String title;
    private String description;
    private String category;
    private final List<String> categories = new ArrayList<>();
    private String language;
    private String link;
    private String copyright;
    private String generator;
    private String ttl;
    private String pubDate;
    private String lastBuildDate;
    private String managingEditor;
    private String webMaster;
    private String docs;
    private String rating;
    private Image image;
    protected String syUpdatePeriod;
    protected int syUpdateFrequency = 1;
    private final DateTimeParser dateTimeParser;

    /**
     * Constructor for Channel
     * @deprecated
     * Use {@link Channel#Channel(DateTimeParser)} instead.
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(since="3.5.0", forRemoval=true)
    public Channel() {
        dateTimeParser = Default.getDateTimeParser();
    }

    /**
     * Constructor for Channel
     * @param dateTimeParser dateTimeParser
     */
    public Channel(DateTimeParser dateTimeParser) {
        this.dateTimeParser = dateTimeParser;
    }

    /**
     * Get the name of the channel. It's how people refer to your service. If you have an HTML website that contains the same information as your RSS file, the title of your channel should be the same as the title of your website.
     * @return title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the name of the channel. It's how people refer to your service. If you have an HTML website that contains the same information as your RSS file, the title of your channel should be the same as the title of your website.
     * @param title title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get phrase or sentence describing the channel.
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set phrase or sentence describing the channel.
     * @param description channel description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get category for the channel.
     *
     * @deprecated
     * This method be removed in a future version.
     * <p> Use {@link Channel#getCategories()} instead.
     *
     * @return category
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(since="3.3.0", forRemoval=true)
    public Optional<String> getCategory() {
        return Optional.ofNullable(category);
    }

    /**
     * Set category for the channel.
     *
     * @deprecated
     * This method be removed in a future version.
     * <p> Use {@link Channel#addCategory(String category)} instead.
     *
     * @param category channel category
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(since="3.3.0", forRemoval=true)
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Get categories for the channel.
     * @return list of categories
     */
    public List<String> getCategories() {
        return Collections.unmodifiableList(categories);
    }

    /**
     * Add category for the channel.
     * @param category channel category
     */
    public void addCategory(String category) {
        this.category = category;
        categories.add(category);
    }

    /**
     * Get the language the channel is written in.
     * @return language
     */
    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    /**
     * Set the language the channel is written in.
     * @param language language
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Get the URL to the HTML website corresponding to the channel.
     * @return link
     */
    public String getLink() {
        return link;
    }

    /**
     * Set the URL to the HTML website corresponding to the channel.
     * @param link URL
     */
    public void setLink(String link) {
        this.link = link;
    }

    /**
     * Get copyright notice for content in the channel.
     * @return URL
     */
    public Optional<String> getCopyright() {
        return Optional.ofNullable(copyright);
    }

    /**
     * Set copyright notice for content in the channel.
     * @param copyright copyright
     */
    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    /**
     * Get a string indicating the program used to generate the channel.
     * @return generator
     */
    public Optional<String> getGenerator() {
        return Optional.ofNullable(generator);
    }

    /**
     * Set a string indicating the program used to generate the channel.
     * @param generator generator
     */
    public void setGenerator(String generator) {
        this.generator = generator;
    }

    /**
     * Get ttl (time to live). It's a number of minutes that indicates how long a channel can be cached before
     * refreshing from the source.
     * @return time to live
     */
    public Optional<String> getTtl() {
        return Optional.ofNullable(ttl)
                .or(() -> Optional.ofNullable(syUpdatePeriod)
                        .map(Util::toMinutes)
                        .map(minutes -> minutes / Math.max(syUpdateFrequency, 1))
                        .map(String::valueOf));
    }

    /**
     * Set ttl (time to live). It's a number of minutes that indicates how long a channel can be cached before
     * refreshing from the source.
     * @param ttl time to live
     */
    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    /**
     * Get the publication date for the content in the channel.
     * @return publication date
     */
    public Optional<String> getPubDate() {
        return Optional.ofNullable(pubDate);
    }

    /**
     * Get the publication date for the content in the channel.
     * @return publication date
     */
    public Optional<ZonedDateTime> getPubDateZonedDateTime() {
        return getPubDate().map(dateTimeParser::parse);
    }

    /**
     * Set the publication date for the content in the channel.
     * @param pubDate publication date
     */
    public void setPubDate(String pubDate) {
        this.pubDate = pubDate;
    }

    /**
     * Get the last time the content of the channel changed.
     * @return last build date
     */
    public Optional<String> getLastBuildDate() {
        return Optional.ofNullable(lastBuildDate);
    }

    /**
     * Get the last time the content of the channel changed.
     * @return last build date
     */
    public Optional<ZonedDateTime> getLastBuildDateZonedDateTime() {
        return getLastBuildDate().map(dateTimeParser::parse);
    }

    /**
     * Set the last time the content of the channel changed.
     * @param lastBuildDate last build date
     */
    public void setLastBuildDate(String lastBuildDate) {
        this.lastBuildDate = lastBuildDate;
    }

    /**
     * Get email address for person responsible for editorial content.
     * @return managing editor
     */
    public Optional<String> getManagingEditor() {
        return Optional.ofNullable(managingEditor);
    }

    /**
     * Set email address for person responsible for editorial content.
     * @param managingEditor managing editor
     */
    public void setManagingEditor(String managingEditor) {
        this.managingEditor = managingEditor;
    }

    /**
     * Get email address for person responsible for technical issues relating to channel.
     * @return web master
     */
    public Optional<String> getWebMaster() {
        return Optional.ofNullable(webMaster);
    }

    /**
     * Set email address for person responsible for technical issues relating to channel.
     * @param webMaster web master
     */
    public void setWebMaster(String webMaster) {
        this.webMaster = webMaster;
    }

    /**
     * Get the documentation for the format used in the RSS file.
     * @return documentation
     */
    public String getDocs() {
        return docs;
    }

    /**
     * Set  the documentation for the format used in the RSS file.
     * @param docs documentation
     */
    public void setDocs(String docs) {
        this.docs = docs;
    }

    /**
     * Get the PICS rating for the channel.
     * @return rating
     */
    public String getRating() {
        return rating;
    }

    /**
     * Set the PICS rating for the channel.
     * @param rating rating
     */
    public void setRating(String rating) {
        this.rating = rating;
    }

    /**
     * Get a GIF, JPEG or PNG image that can be displayed with the channel.
     * @return image
     */
    public Optional<Image> getImage() {
        return Optional.ofNullable(image);
    }

    /**
     * Set a GIF, JPEG or PNG image that can be displayed with the channel.
     * @param image image
     */
    public void setImage(Image image) {
        this.image = image;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Objects.equals(getTitle(), channel.getTitle()) &&
                Objects.equals(getDescription(), channel.getDescription()) &&
                getCategories().equals(channel.getCategories()) &&
                Objects.equals(getLanguage(), channel.getLanguage()) &&
                Objects.equals(getLink(), channel.getLink()) &&
                Objects.equals(getCopyright(), channel.getCopyright()) &&
                Objects.equals(getGenerator(), channel.getGenerator()) &&
                Objects.equals(getTtl(), channel.getTtl()) &&
                Objects.equals(getPubDate(), channel.getPubDate()) &&
                Objects.equals(getLastBuildDate(), channel.getLastBuildDate()) &&
                Objects.equals(getManagingEditor(), channel.getManagingEditor()) &&
                Objects.equals(getWebMaster(), channel.getWebMaster()) &&
                Objects.equals(getDocs(), channel.getDocs()) &&
                Objects.equals(getRating(), channel.getRating()) &&
                Objects.equals(getImage(), channel.getImage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTitle(), getDescription(), getCategories(), getLanguage(), getLink(),
                getCopyright(), getGenerator(), getTtl(), getPubDate(), getLastBuildDate(),
                getManagingEditor(), getWebMaster(), getDocs(), getRating(), getImage());
    }
}
