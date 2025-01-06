package com.apptasticsoftware.rssreader.module.mediarss;

import java.util.Optional;

/**
 * Class representing the media thumbnail from the media rss spec.
 * See <a href="https://www.rssboard.org/media-rss#media-thumbnails">for details</a>.
 */
public class MediaThumbnail {
    private String url;
    private Integer width;
    private Integer height;
    private String time;

    /**
     * Get the url of the thumbnail
     *
     * @return url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the url of the thumbnail
     *
     * @param url url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get the width of the thumbnail
     *
     * @return width
     */
    public Optional<Integer> getWidth() {
        return Optional.ofNullable(width);
    }

    /**
     * Set the width of the thumbnail
     *
     * @param width width
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * Get the height of the thumbnail
     *
     * @return height
     */
    public Optional<Integer> getHeight() {
        return Optional.ofNullable(height);
    }

    /**
     * Set the height of the thumbnail
     *
     * @param height height
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * Get the time of the thumbnail
     *
     * @return time
     */
    public Optional<String> getTime() {
        return Optional.ofNullable(time);
    }

    /**
     * Set the time of the thumbnail
     *
     * @param time time
     */
    public void setTime(String time) {
        this.time = time;
    }
}
