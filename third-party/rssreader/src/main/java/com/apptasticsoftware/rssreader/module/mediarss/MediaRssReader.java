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

import com.apptasticsoftware.rssreader.AbstractRssReader;
import com.apptasticsoftware.rssreader.Channel;
import com.apptasticsoftware.rssreader.DateTimeParser;

import java.net.http.HttpClient;
import java.util.function.BiConsumer;

/**
 * Class for reading media rss feeds.
 */
public class MediaRssReader extends AbstractRssReader<Channel, MediaRssItem> {

    /**
     * Constructor
     */
    public MediaRssReader() {
        super();
    }

    /**
     * Constructor
     * @param httpClient http client
     */
    public MediaRssReader(HttpClient httpClient) {
        super(httpClient);
    }

    @Override
    protected Channel createChannel(DateTimeParser dateTimeParser) {
        return new Channel(dateTimeParser);
    }

    @Override
    protected MediaRssItem createItem(DateTimeParser dateTimeParser) {
        return new MediaRssItem(dateTimeParser);
    }

    @SuppressWarnings("java:S1192")
    @Override
    protected void registerItemAttributes() {
        super.registerItemAttributes();
        super.addItemExtension("media:thumbnail", "url", mediaThumbnailSetterTemplateBuilder(MediaThumbnail::setUrl));
        super.addItemExtension("media:thumbnail", "height", mediaThumbnailSetterTemplateBuilder(
                (mediaThumbnail, height) -> mediaThumbnail.setHeight(Integer.parseInt(height))
        ));
        super.addItemExtension("media:thumbnail", "width", mediaThumbnailSetterTemplateBuilder(
                (mediaThumbnail, width) -> mediaThumbnail.setWidth(Integer.parseInt(width))
        ));
    }

    private BiConsumer<MediaRssItem, String> mediaThumbnailSetterTemplateBuilder(BiConsumer<MediaThumbnail, String> setter) {
        return (mediaRssItem, value) -> {
            var mediaThumbnail = mediaRssItem.getMediaThumbnail().orElse(new MediaThumbnail());
            setter.accept(mediaThumbnail, value);
            mediaRssItem.setMediaThumbnail(mediaThumbnail);
        };
    }
}
