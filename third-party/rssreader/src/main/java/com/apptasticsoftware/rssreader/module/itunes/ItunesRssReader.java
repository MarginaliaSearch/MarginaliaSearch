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

import com.apptasticsoftware.rssreader.AbstractRssReader;
import com.apptasticsoftware.rssreader.DateTimeParser;

import java.net.http.HttpClient;

import static com.apptasticsoftware.rssreader.util.Mapper.mapBoolean;
import static com.apptasticsoftware.rssreader.util.Mapper.mapInteger;

/**
 * Class for reading podcast (itunes) feeds.
 */
public class ItunesRssReader extends AbstractRssReader<ItunesChannel, ItunesItem> {

    /**
     * Constructor
     */
    public ItunesRssReader() {
        super();
    }

    /**
     * Constructor
     * @param httpClient http client
     */
    public ItunesRssReader(HttpClient httpClient) {
        super(httpClient);
    }

    @Override
    protected void registerChannelTags() {
        super.registerChannelTags();
        addChannelExtension("itunes:explicit", (i, v) -> mapBoolean(v, i::setItunesExplicit));
        addChannelExtension("itunes:author", ItunesChannel::setItunesAuthor);

        addChannelExtension("itunes:name", (i, v) -> {
            if (i.getItunesOwner().isEmpty())
                i.setItunesOwner(new ItunesOwner());
            i.getItunesOwner().ifPresent(a -> a.setName(v));
        });

        addChannelExtension("itunes:email", (i, v) -> {
            if (i.getItunesOwner().isEmpty())
                i.setItunesOwner(new ItunesOwner());
            i.getItunesOwner().ifPresent(a -> a.setEmail(v));
        });

        addChannelExtension("itunes:title", ItunesChannel::setItunesTitle);
        addChannelExtension("itunes:subtitle", ItunesChannel::setItunesSubtitle);
        addChannelExtension("itunes:summary", ItunesChannel::setItunesSummary);
        addChannelExtension("itunes:type", ItunesChannel::setItunesType);
        addChannelExtension("itunes:new-feed-url", ItunesChannel::setItunesNewFeedUrl);
        addChannelExtension("itunes:block", (i, v) -> mapBoolean(v, i::setItunesBlock));
        addChannelExtension("itunes:complete", (i, v) -> mapBoolean(v, i::setItunesComplete));
    }

    @Override
    protected void registerChannelAttributes() {
        super.registerChannelAttributes();
        addChannelExtension("itunes:image", "href", ItunesChannel::setItunesImage);
        addChannelExtension("itunes:category", "text", ItunesChannel::addItunesCategory);
    }

    @Override
    protected void registerItemTags() {
        super.registerItemTags();
        addItemExtension("itunes:duration", ItunesItem::setItunesDuration);
        addItemExtension("itunes:explicit", (i, v) -> mapBoolean(v, i::setItunesExplicit));
        addItemExtension("itunes:title", ItunesItem::setItunesTitle);
        addItemExtension("itunes:subtitle", ItunesItem::setItunesSubtitle);
        addItemExtension("itunes:summary", ItunesItem::setItunesSummary);
        addItemExtension("itunes:keywords", ItunesItem::setItunesKeywords);
        addItemExtension("itunes:episode", (i, v) -> mapInteger(v, i::setItunesEpisode));
        addItemExtension("itunes:season", (i, v) -> mapInteger(v, i::setItunesSeason));
        addItemExtension("itunes:episodeType", ItunesItem::setItunesEpisodeType);
        addItemExtension("itunes:block", (i, v) -> mapBoolean(v, i::setItunesBlock));
        addItemExtension("itunes:image", "href", ItunesItem::setItunesImage);
    }

    @Override
    protected ItunesChannel createChannel(DateTimeParser dateTimeParser) {
        return new ItunesChannel(dateTimeParser);
    }

    @Override
    protected ItunesItem createItem(DateTimeParser dateTimeParser) {
        return new ItunesItem(dateTimeParser);
    }
}
