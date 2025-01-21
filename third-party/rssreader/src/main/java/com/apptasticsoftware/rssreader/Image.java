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

import java.util.Objects;
import java.util.Optional;

/**
 * Class representing a image in channel.
 */
public class Image {
    private String title;
    private String link;
    private String url;
    private String description;
    private Integer height;
    private Integer width;

    /**
     * Get title that describes the image.
     * @return title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set title that describes the image.
     * @param title title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get the URL of the site.
     * @return link
     */
    public String getLink() {
        return link;
    }

    /**
     * Set the URL of the site.
     * @param link link
     */
    public void setLink(String link) {
        this.link = link;
    }

    /**
     * Get the URL of a GIF, JPEG or PNG image that represents the channel.
     * @return url to image
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the URL of a GIF, JPEG or PNG image that represents the channel.
     * @param url url to image
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get the description.
     * @return description
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * Set the description.
     * @param description description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the height of the image.
     * @return image height
     */
    public Optional<Integer> getHeight() {
        return Optional.ofNullable(height);
    }

    /**
     * Set the height of the image.
     * @param height image height
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * Get the width of the image.
     * @return image width
     */
    public Optional<Integer> getWidth() {
        return Optional.ofNullable(width);
    }

    /**
     * Set the width of the image.
     * @param width image width
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image image = (Image) o;
        return Objects.equals(getTitle(), image.getTitle()) && Objects.equals(getLink(), image.getLink()) &&
                Objects.equals(getUrl(), image.getUrl()) && Objects.equals(getDescription(), image.getDescription()) &&
                Objects.equals(getHeight(), image.getHeight()) && Objects.equals(getWidth(), image.getWidth());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTitle(), getLink(), getUrl(), getDescription(), getHeight(), getWidth());
    }
}
