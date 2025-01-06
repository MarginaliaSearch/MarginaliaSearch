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
package com.apptasticsoftware.rssreader.module.mediarss;

import com.apptasticsoftware.rssreader.DateTimeParser;
import com.apptasticsoftware.rssreader.Item;

import java.util.Objects;
import java.util.Optional;

/**
 * Class representing the media rss item.
 */
public class MediaRssItem extends Item {
    private MediaThumbnail mediaThumbnail;

    /**
     * Constructor
     *
     * @param dateTimeParser timestamp parser
     */
    public MediaRssItem(DateTimeParser dateTimeParser) {
        super(dateTimeParser);
    }

    /**
     * Get the media thumbnail
     *
     * @return media thumbnail
     */
    public Optional<MediaThumbnail> getMediaThumbnail() {
        return Optional.ofNullable(mediaThumbnail);
    }

    /**
     * Set the media thumbnail
     *
     * @param mediaThumbnail media thumbnail
     */
    public void setMediaThumbnail(MediaThumbnail mediaThumbnail) {
        this.mediaThumbnail = mediaThumbnail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MediaRssItem that = (MediaRssItem) o;
        return Objects.equals(getMediaThumbnail(), that.getMediaThumbnail());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getMediaThumbnail());
    }
}
