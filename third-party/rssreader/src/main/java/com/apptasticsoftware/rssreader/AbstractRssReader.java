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

import com.apptasticsoftware.rssreader.internal.DaemonThreadFactory;
import com.apptasticsoftware.rssreader.internal.StreamUtil;
import com.apptasticsoftware.rssreader.internal.XMLInputFactorySecurity;
import com.apptasticsoftware.rssreader.internal.stream.AutoCloseStream;
import com.apptasticsoftware.rssreader.util.Default;
import com.apptasticsoftware.rssreader.util.Mapper;

import javax.net.ssl.SSLContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.lang.ref.Cleaner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.apptasticsoftware.rssreader.util.Mapper.*;
import static javax.xml.stream.XMLStreamConstants.*;


/**
 * Abstract base class for implementing modules or extensions of RSS / Atom feeds with custom tags and attributes.
 */
public abstract class AbstractRssReader<C extends Channel, I extends Item> {
    private static final Logger LOGGER = Logger.getLogger("com.apptasticsoftware.rssreader");
    private static final ScheduledExecutorService EXECUTOR = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("RssReaderWorker"));
    private static final Cleaner CLEANER = Cleaner.create();
    private final HttpClient httpClient;
    private DateTimeParser dateTimeParser = Default.getDateTimeParser();
    private String userAgent = "";
    private Duration connectionTimeout = Duration.ofSeconds(25);
    private Duration requestTimeout = Duration.ofSeconds(25);
    private Duration readTimeout = Duration.ofSeconds(25);
    private final Map<String, String> headers = new HashMap<>();
    private final HashMap<String, BiConsumer<C, String>> channelTags = new HashMap<>();
    private final HashMap<String, Map<String, BiConsumer<C, String>>> channelAttributes = new HashMap<>();
    private final HashMap<String, Consumer<I>> onItemTags = new HashMap<>();
    private final HashMap<String, BiConsumer<I, String>> itemTags = new HashMap<>();
    private final HashMap<String, Map<String, BiConsumer<I, String>>> itemAttributes = new HashMap<>();
    private final Set<String> collectChildNodesForTag = Set.of("content", "summary", "title");
    private boolean isInitialized;

    /**
     * Constructor
     */
    protected AbstractRssReader() {
        httpClient = createHttpClient();
    }

    /**
     * Constructor
     * @param httpClient http client
     */
    protected AbstractRssReader(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "Http client must not be null");
        this.httpClient = httpClient;
    }

    /**
     * Returns an object of a Channel implementation.
     *
     * @deprecated
     * Use {@link AbstractRssReader#createChannel(DateTimeParser)} instead.
     *
     * @return channel
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(since="3.5.0", forRemoval=true)
    protected C createChannel() {
        return null;
    }

    /**
     * Returns an object of a Channel implementation.
     *
     * @param dateTimeParser dateTimeParser
     * @return channel
     */
    protected abstract C createChannel(DateTimeParser dateTimeParser);

    /**
     * Returns an object of an Item implementation.
     *
     * @deprecated
     * Use {@link AbstractRssReader#createItem(DateTimeParser)} instead.
     *
     * @return item
     */
    @SuppressWarnings("java:S1133")
    @Deprecated(since="3.5.0", forRemoval=true)
    protected I createItem() {
        return null;
    }

    /**
     * Returns an object of an Item implementation.
     *
     * @param dateTimeParser dateTimeParser
     * @return item
     */
    protected abstract I createItem(DateTimeParser dateTimeParser);

    /**
     * Initialize channel and items tags and attributes
     */
    protected void initialize() {
        registerChannelTags();
        registerChannelAttributes();
        registerItemTags();
        registerItemAttributes();
    }

    /**
     * Register channel tags for mapping to channel object fields
     */
    @SuppressWarnings("java:S1192")
    protected void registerChannelTags() {
        channelTags.putIfAbsent("title", (channel, value) -> Mapper.mapIfEmpty(value, channel::getTitle, channel::setTitle));
        channelTags.putIfAbsent("description", (channel, value) -> Mapper.mapIfEmpty(value, channel::getDescription, channel::setDescription));
        channelTags.putIfAbsent("/feed/title", Channel::setTitle);
        channelTags.putIfAbsent("/rss/channel/title", Channel::setTitle);
        channelTags.putIfAbsent("/rss/channel/description", Channel::setDescription);
        channelTags.putIfAbsent("subtitle", Channel::setDescription);
        channelTags.putIfAbsent("link", Channel::setLink);
        channelTags.putIfAbsent("category", Channel::addCategory);
        channelTags.putIfAbsent("language", Channel::setLanguage);
        channelTags.putIfAbsent("copyright", Channel::setCopyright);
        channelTags.putIfAbsent("rights", Channel::setCopyright);
        channelTags.putIfAbsent("generator", Channel::setGenerator);
        channelTags.putIfAbsent("ttl", Channel::setTtl);
        channelTags.putIfAbsent("pubDate", Channel::setPubDate);
        channelTags.putIfAbsent("lastBuildDate", Channel::setLastBuildDate);
        channelTags.putIfAbsent("updated", Channel::setLastBuildDate);
        channelTags.putIfAbsent("managingEditor", Channel::setManagingEditor);
        channelTags.putIfAbsent("webMaster", Channel::setWebMaster);
        channelTags.putIfAbsent("docs", Channel::setDocs);
        channelTags.putIfAbsent("rating", Channel::setRating);
        channelTags.putIfAbsent("/rss/channel/image/link", (channel, value) -> createIfNull(channel::getImage, channel::setImage, Image::new).setLink(value));
        channelTags.putIfAbsent("/rss/channel/image/title", (channel, value) -> createIfNull(channel::getImage, channel::setImage, Image::new).setTitle(value));
        channelTags.putIfAbsent("/rss/channel/image/url", (channel, value) -> createIfNull(channel::getImage, channel::setImage, Image::new).setUrl(value));
        channelTags.putIfAbsent("/rss/channel/image/description", (channel, value) -> createIfNullOptional(channel::getImage, channel::setImage, Image::new).ifPresent(i -> i.setDescription(value)));
        channelTags.putIfAbsent("/rss/channel/image/height", (channel, value) -> createIfNullOptional(channel::getImage, channel::setImage, Image::new).ifPresent(i -> mapInteger(value, i::setHeight)));
        channelTags.putIfAbsent("/rss/channel/image/width", (channel, value) -> createIfNullOptional(channel::getImage, channel::setImage, Image::new).ifPresent(i -> mapInteger(value, i::setWidth)));
        channelTags.putIfAbsent("dc:language", (channel, value) -> Mapper.mapIfEmpty(value, channel::getLanguage, channel::setLanguage));
        channelTags.putIfAbsent("dc:rights", (channel, value) -> Mapper.mapIfEmpty(value, channel::getCopyright, channel::setCopyright));
        channelTags.putIfAbsent("dc:title", (channel, value) -> Mapper.mapIfEmpty(value, channel::getTitle, channel::setTitle));
        channelTags.putIfAbsent("sy:updatePeriod", (channel, value) -> channel.syUpdatePeriod = value);
        channelTags.putIfAbsent("sy:updateFrequency", (channel, value) -> mapInteger(value, number -> channel.syUpdateFrequency = number));
        channelTags.putIfAbsent("/feed/icon", (channel, value) -> createIfNull(channel::getImage, channel::setImage, Image::new).setUrl(value));
        channelTags.putIfAbsent("/feed/logo", (channel, value) -> createIfNull(channel::getImage, channel::setImage, Image::new).setUrl(value));
    }

    /**
     * Register channel attributes for mapping to channel object fields
     */
    protected void registerChannelAttributes() {
        channelAttributes.computeIfAbsent("link", k -> new HashMap<>()).put("href", Channel::setLink);
        channelAttributes.computeIfAbsent("category", k -> new HashMap<>()).putIfAbsent("term", Channel::addCategory);
    }

    /**
     * Register item tags for mapping to item object fields
     */
    @SuppressWarnings("java:S1192")
    protected void registerItemTags() {
        itemTags.putIfAbsent("guid", Item::setGuid);
        itemTags.putIfAbsent("id", Item::setGuid);
        itemTags.putIfAbsent("title", (item, value) -> Mapper.mapIfEmpty(value, item::getTitle, item::setTitle));
        itemTags.putIfAbsent("/feed/entry/title", Item::setTitle);
        itemTags.putIfAbsent("/rss/channel/item/title", Item::setTitle);
        itemTags.putIfAbsent("description", Item::setDescription);
        itemTags.putIfAbsent("summary", Item::setDescription);
        itemTags.putIfAbsent("content", Item::setContent);
        itemTags.putIfAbsent("content:encoded", (item, value) -> Mapper.mapIfEmpty(value, item::getContent, item::setContent));
        itemTags.putIfAbsent("link", Item::setLink);
        itemTags.putIfAbsent("author", Item::setAuthor);
        itemTags.putIfAbsent("/feed/entry/author/name", Item::setAuthor);
        itemTags.putIfAbsent("category", Item::addCategory);
        itemTags.putIfAbsent("pubDate", Item::setPubDate);
        itemTags.putIfAbsent("published", Item::setPubDate);
        itemTags.putIfAbsent("updated", (item, value) -> {
            item.setUpdated(value);
            Mapper.mapIfEmpty(value, item::getPubDate, item::setPubDate);
        });
        itemTags.putIfAbsent("comments", Item::setComments);
        itemTags.putIfAbsent("dc:creator", (item, value) -> Mapper.mapIfEmpty(value, item::getAuthor, item::setAuthor));
        itemTags.putIfAbsent("dc:date", (item, value) -> Mapper.mapIfEmpty(value, item::getPubDate, item::setPubDate));
        itemTags.putIfAbsent("dc:identifier", (item, value) -> Mapper.mapIfEmpty(value, item::getGuid, item::setGuid));
        itemTags.putIfAbsent("dc:title", (item, value) -> Mapper.mapIfEmpty(value, item::getTitle, item::setTitle));
        itemTags.putIfAbsent("dc:description", (item, value) -> Mapper.mapIfEmpty(value, item::getDescription, item::setDescription));

        onItemTags.put("enclosure", item -> item.addEnclosure(new Enclosure()));
    }

    /**
     * Register item attributes for mapping to item object fields
     */
    protected void registerItemAttributes() {
        itemAttributes.computeIfAbsent("link", k -> new HashMap<>()).putIfAbsent("href", Item::setLink);
        itemAttributes.computeIfAbsent("guid", k -> new HashMap<>()).putIfAbsent("isPermaLink", (item, value) -> item.setIsPermaLink(Boolean.parseBoolean(value)) );
        itemAttributes.computeIfAbsent("category", k -> new HashMap<>()).putIfAbsent("term", Item::addCategory);

        var enclosureAttributes = itemAttributes.computeIfAbsent("enclosure", k -> new HashMap<>());
        enclosureAttributes.putIfAbsent("url", (item, value) -> item.getEnclosure().ifPresent(a -> a.setUrl(value)));
        enclosureAttributes.putIfAbsent("type", (item, value) -> item.getEnclosure().ifPresent(a -> a.setType(value)));
        enclosureAttributes.putIfAbsent("length", (item, value) -> item.getEnclosure().ifPresent(e -> mapLong(value, e::setLength)));
    }

    /**
     * Date and time parser for parsing timestamps.
     * @param dateTimeParser the date time parser to use.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> setDateTimeParser(DateTimeParser dateTimeParser) {
        Objects.requireNonNull(dateTimeParser, "Date time parser must not be null");

        this.dateTimeParser = dateTimeParser;
        return this;
    }

    /**
     * Sets the user-agent of the http client.
     * Optional parameter if not set the default value for {@code java.net.http.HttpClient} will be used.
     * @param userAgent the user-agent to use.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> setUserAgent(String userAgent) {
        Objects.requireNonNull(userAgent, "User-agent must not be null");

        this.userAgent = userAgent;
        return this;
    }

    /**
     * Adds a http header to the http client.
     * @param key the key name of the header.
     * @param value the value of the header.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> addHeader(String key, String value) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");

        this.headers.put(key, value);
        return this;
    }

    /**
     * Sets the connection timeout for the http client.
     * The connection timeout is the time it takes to establish a connection to the server.
     * If set to zero the default value for {@link java.net.http.HttpClient.Builder#connectTimeout(Duration)} will be used.
     * Default: 25 seconds.
     *
     * @param connectionTimeout the timeout duration.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> setConnectionTimeout(Duration connectionTimeout) {
        validate(connectionTimeout, "Connection timeout");
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Sets the request timeout for the http client.
     * The request timeout is the time between the request is sent and the first byte of the response is received.
     * If set to zero the default value for {@link java.net.http.HttpRequest.Builder#timeout(Duration)} will be used.
     * Default: 25 seconds.
     *
     * @param requestTimeout the timeout duration.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> setRequestTimeout(Duration requestTimeout) {
        validate(requestTimeout, "Request timeout");
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * Sets the read timeout.
     * The read timeout it the time for reading all data in the response body.
     * The effect of setting the timeout to zero is the same as setting an infinite Duration, ie. block forever.
     * Default: 25 seconds.
     *
     * @param readTimeout the timeout duration.
     * @return updated RSSReader.
     */
    public AbstractRssReader<C, I> setReadTimeout(Duration readTimeout) {
        validate(readTimeout, "Read timeout");
        this.readTimeout = readTimeout;
        return this;
    }

    private void validate(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");

        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    /**
     * Add item extension for tags
     * @param tag - tag name
     * @param consumer - setter method in Item class to use for mapping
     * @return this instance
     */
    public AbstractRssReader<C, I> addItemExtension(String tag, BiConsumer<I, String> consumer) {
        Objects.requireNonNull(tag, "Item tag must not be null");
        Objects.requireNonNull(consumer, "Item consumer must not be null");

        itemTags.put(tag, consumer);
        return this;
    }

    /**
     * Add item extension for attributes
     * @param tag - tag name
     * @param attribute - attribute name
     * @param consumer - setter method in Item class to use for mapping
     * @return this instance
     */
    public AbstractRssReader<C, I> addItemExtension(String tag, String attribute, BiConsumer<I, String> consumer) {
        Objects.requireNonNull(tag, "Item tag must not be null");
        Objects.requireNonNull(attribute, "Item attribute must not be null");
        Objects.requireNonNull(consumer, "Item consumer must not be null");

        itemAttributes.computeIfAbsent(tag, k -> new HashMap<>())
                .put(attribute, consumer);
        return this;
    }

    /**
     * Add channel extension for tags
     * @param tag - tag name
     * @param consumer - setter method in Channel class to use for mapping
     * @return this instance
     */
    public AbstractRssReader<C, I> addChannelExtension(String tag, BiConsumer<C, String> consumer) {
        Objects.requireNonNull(tag, "Channel tag must not be null");
        Objects.requireNonNull(consumer, "Channel consumer must not be null");

        channelTags.put(tag, consumer);
        return this;
    }

    /**
     * Add channel extension for attributes
     * @param tag - tag name
     * @param attribute - attribute name
     * @param consumer - setter method in Channel class to use for mapping
     * @return this instance
     */
    public AbstractRssReader<C, I> addChannelExtension(String tag, String attribute, BiConsumer<C, String> consumer) {
        Objects.requireNonNull(tag, "Channel tag must not be null");
        Objects.requireNonNull(attribute, "Channel attribute must not be null");
        Objects.requireNonNull(consumer, "Channel consumer must not be null");

        channelAttributes.computeIfAbsent(tag, k -> new HashMap<>())
                .put(attribute, consumer);
        return this;
    }

    /**
     * Read RSS feed with the given URL or file URI.
     * @param url URL to RSS feed or file URI.
     * @return Stream of items
     * @throws IOException Fail to read url or its content
     */
    @SuppressWarnings("squid:S1181")
    public Stream<I> read(String url) throws IOException {
        Objects.requireNonNull(url, "URL must not be null");

        try {
            return readAsync(url).get(1, TimeUnit.MINUTES);
        } catch (CompletionException e) {
            try {
                throw e.getCause();
            } catch (IOException e2) {
                throw e2;
            } catch(Throwable e2) {
                throw new AssertionError(e2);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(e);
        }
    }

    /**
     * Read from a collections of RSS feed.
     * @param urls collections of URLs or file URIs
     * @return Stream of items
     */
    public Stream<Item> read(Collection<String> urls) {
        Objects.requireNonNull(urls, "URLs collection must not be null");
        urls.forEach(url -> Objects.requireNonNull(url, "URL must not be null. Url: " + url));

        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }
        return AutoCloseStream.of(urls.stream()
                .parallel()
                .map(url -> {
                    try {
                        return Map.entry(url, readAsync(url));
                    } catch (Exception e) {
                        if (LOGGER.isLoggable(Level.WARNING)) {
                            LOGGER.log(Level.WARNING, () -> String.format("Failed read URL %s. Message: %s", url, e.getMessage()));
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(f -> {
                    try {
                        return f.getValue().join();
                    } catch (Exception e) {
                        if (LOGGER.isLoggable(Level.WARNING)) {
                            LOGGER.log(Level.WARNING, () -> String.format("Failed to read URL %s. Message: %s", f.getKey(), e.getMessage()));
                        }
                        return Stream.empty();
                    }
                }));
    }

    /**
     * Read RSS feed from input stream.
     * @param inputStream inputStream containing the RSS feed.
     * @return Stream of items
     */
    public Stream<I> read(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "Input stream must not be null");

        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }

        inputStream = new BufferedInputStream(inputStream);
        removeBadData(inputStream);
        var itemIterator = new RssItemIterator(inputStream);
        return AutoCloseStream.of(StreamUtil.asStream(itemIterator).onClose(itemIterator::close));
    }

    /**
     * Read RSS feed asynchronous with the given URL.
     * @param url URL to RSS feed.
     * @return Stream of items
     */
    public CompletableFuture<Stream<I>> readAsync(String url) {
        Objects.requireNonNull(url, "URL must not be null");

        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }

        try {
            var uri = URI.create(url);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                // Read from file
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return read(new FileInputStream(uri.getPath()));
                    } catch (FileNotFoundException e) {
                        throw new CompletionException(e);
                    }
                });
            } else {
                // Read from http or https
                return sendAsyncRequest(url).thenApply(processResponse());
            }
        } catch (IllegalArgumentException e) {
            return CompletableFuture.supplyAsync(() -> {
                // Read feed data provided as a string
                var inputStream = new ByteArrayInputStream(url.getBytes(StandardCharsets.UTF_8));
                return read(inputStream);
            });
        }
    }

    /**
     * Sends request
     * @param url url
     * @return response
     */
    protected CompletableFuture<HttpResponse<InputStream>> sendAsyncRequest(String url) {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept-Encoding", "gzip");
        if (requestTimeout.toMillis() > 0) {
            builder.timeout(requestTimeout);
        }

        if (!userAgent.isBlank()) {
            builder.header("User-Agent", userAgent);
        }

        headers.forEach(builder::header);
        return httpClient.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private Function<HttpResponse<InputStream>, Stream<I>> processResponse() {
        return response -> {
            try {
                if (response.statusCode() >= 400 && response.statusCode() < 600) {
                    throw new IOException(String.format("Response HTTP status code: %d", response.statusCode()));
                }

                var inputStream = response.body();
                if ("gzip".equals(response.headers().firstValue("Content-Encoding").orElse(null))) {
                    inputStream = new GZIPInputStream(inputStream);
                }

                inputStream = new BufferedInputStream(inputStream);
                removeBadData(inputStream);
                var itemIterator = new RssItemIterator(inputStream);
                return AutoCloseStream.of(StreamUtil.asStream(itemIterator).onClose(itemIterator::close));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        };
    }

    @SuppressWarnings({"java:S108", "java:S2674"})
    private void removeBadData(InputStream inputStream) {
        try {
            inputStream.mark(128);
            long count = 0;
            int data = inputStream.read();
            while (Character.isWhitespace(data)) {
                data = inputStream.read();
                ++count;
            }
            inputStream.reset();
            inputStream.skip(count);
        } catch (IOException ignore) { }
    }

    private static class CleaningAction implements Runnable {
        private final XMLStreamReader xmlStreamReader;
        private final List<AutoCloseable> resources;

        public CleaningAction(XMLStreamReader xmlStreamReader, AutoCloseable... resources) {
            this.xmlStreamReader = xmlStreamReader;
            this.resources = List.of(resources);
        }

        @Override
        public void run() {
            try {
                if (xmlStreamReader != null) {
                    xmlStreamReader.close();
                }
            } catch (XMLStreamException e) {
                LOGGER.log(Level.WARNING, "Failed to close XML stream. ", e);
            }

            for (AutoCloseable resource : resources) {
                try {
                    if (resource != null) {
                        resource.close();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to close resource. ", e);
                }
            }
        }
    }

    class RssItemIterator implements Iterator<I>, AutoCloseable {
        private final StringBuilder textBuilder;
        private final Map<String, StringBuilder> childNodeTextBuilder;
        private final Deque<String> elementStack;
        private XMLStreamReader reader;
        private C channel;
        private I item = null;
        private I nextItem;
        private boolean isChannelPart = false;
        private boolean isItemPart = false;
        private ScheduledFuture<?> parseWatchdog;
        private final AtomicBoolean isClosed;
        private Cleaner.Cleanable cleanable;

        public RssItemIterator(InputStream is) {
            nextItem = null;
            textBuilder = new StringBuilder();
            childNodeTextBuilder = new HashMap<>();
            elementStack = new ArrayDeque<>();
            isClosed = new AtomicBoolean(false);

            try {
                // disable XML external entity (XXE) processing
                var xmlInputFactory = XMLInputFactorySecurity.hardenFactory(XMLInputFactory.newInstance());
                xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
                xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);

                reader = xmlInputFactory.createXMLStreamReader(is);
                cleanable = CLEANER.register(this, new CleaningAction(reader, is));
                if (!readTimeout.isZero()) {
                    parseWatchdog = EXECUTOR.schedule(this::close, readTimeout.toMillis(), TimeUnit.MILLISECONDS);
                }
            }
            catch (XMLStreamException e) {
                LOGGER.log(Level.FINE, "Failed to process XML.", e);
            }
        }

        public void close() {
            if (isClosed.compareAndSet(false, true)) {
                cleanable.clean();
                if (parseWatchdog != null) {
                    parseWatchdog.cancel(false);
                }
            }
        }

        private void peekNext() {
            if (nextItem == null) {
                try {
                    nextItem = next();
                } catch (NoSuchElementException e) {
                    nextItem = null;
                }
            }
        }

        @Override
        public boolean hasNext() {
            peekNext();
            return nextItem != null;
        }

        @Override
        @SuppressWarnings("squid:S3776")
        public I next() {
            if (nextItem != null) {
                var next = nextItem;
                nextItem = null;
                return next;
            }

            try {
                while (reader.hasNext()) {
                    var type = reader.next();
                    collectChildNodes(type);

                    if (type == CHARACTERS || type == CDATA) {
                        parseCharacters();
                    } else if (type == START_ELEMENT) {
                        parseStartElement();
                        parseAttributes();
                    } else if (type == END_ELEMENT) {
                        var itemParsed = parseEndElement();
                        if (itemParsed) {
                            return item;
                        }
                    }
                }
            } catch (XMLStreamException e) {
                LOGGER.log(Level.FINE, "Failed to parse XML.", e);
            }

            close();
            throw new NoSuchElementException();
        }

        private void collectChildNodes(int type) {
            if (type == START_ELEMENT) {
                var nsTagName = toNsName(reader.getPrefix(), reader.getLocalName());

                if (!childNodeTextBuilder.isEmpty()) {
                    StringBuilder startTagBuilder = new StringBuilder("<").append(nsTagName);
                    // Add namespaces to start tag
                    for (int i = 0; i < reader.getNamespaceCount(); ++i) {
                        startTagBuilder.append(" ")
                                .append(toNamespacePrefix(reader.getNamespacePrefix(i)))
                                .append("=")
                                .append(reader.getNamespaceURI(i));
                    }
                    // Add attributes to start tag
                    for (int i = 0; i < reader.getAttributeCount(); ++i) {
                        startTagBuilder.append(" ")
                                .append(toNsName(reader.getAttributePrefix(i), reader.getAttributeLocalName(i)))
                                .append("=")
                                .append(reader.getAttributeValue(i));
                    }
                    startTagBuilder.append(">");
                    var startTag = startTagBuilder.toString();

                    childNodeTextBuilder.entrySet()
                            .stream()
                            .filter(e -> !e.getKey().equals(nsTagName))
                            .forEach(e -> e.getValue().append(startTag));
                }

                // Collect child notes for tag names in this set
                if (collectChildNodesForTag.contains(nsTagName)) {
                    childNodeTextBuilder.put(nsTagName, new StringBuilder());
                }
            } else if (type == CHARACTERS || type == CDATA) {
                childNodeTextBuilder.forEach((k, builder) -> builder.append(reader.getText()));
            } else if (type == END_ELEMENT) {
                var nsTagName = toNsName(reader.getPrefix(), reader.getLocalName());
                var endTag = "</" + nsTagName + ">";
                childNodeTextBuilder.entrySet()
                        .stream()
                        .filter(e -> !e.getKey().equals(nsTagName))
                        .forEach(e -> e.getValue().append(endTag));
            }
        }

        @SuppressWarnings("java:S5738")
        private void parseStartElement() {
            textBuilder.setLength(0);
            var nsTagName = toNsName(reader.getPrefix(), reader.getLocalName());
            elementStack.addLast(nsTagName);

            if (isChannel(nsTagName)) {
                channel = Objects.requireNonNullElse(createChannel(dateTimeParser), createChannel());
                channel.setTitle("");
                channel.setDescription("");
                channel.setLink("");
                isChannelPart = true;
            } else if (isItem(nsTagName)) {
                item = Objects.requireNonNullElse(createItem(dateTimeParser), createItem());
                item.setChannel(channel);
                isChannelPart = false;
                isItemPart = true;
            }
        }

        protected boolean isChannel(String tagName) {
            return "channel".equals(tagName) || "feed".equals(tagName);
        }

        protected boolean isItem(String tagName) {
            return "item".equals(tagName) || "entry".equals(tagName);
        }

        private void parseAttributes() {
            var nsTagName = toNsName(reader.getPrefix(), reader.getLocalName());
            var elementFullPath = getElementFullPath();

            if (isChannelPart) {
                // Map channel attributes
                mapChannelAttributes(nsTagName);
                mapChannelAttributes(elementFullPath);
            } else if (isItemPart) {
                onItemTags.computeIfPresent(nsTagName, (k, f) -> { f.accept(item); return f; });
                onItemTags.computeIfPresent(elementFullPath, (k, f) -> { f.accept(item); return f; });
                // Map item attributes
                mapItemAttributes(nsTagName);
                mapItemAttributes(elementFullPath);
            }
        }

        private void mapChannelAttributes(String key) {
            var consumers = channelAttributes.get(key);
            if (consumers != null && channel != null) {
                consumers.forEach((attributeName, consumer) -> {
                    var attributeValue = Optional.ofNullable(reader.getAttributeValue(null, attributeName));
                    attributeValue.ifPresent(v -> consumer.accept(channel, v));
                });
            }
        }

        private void mapItemAttributes(String key) {
            var consumers = itemAttributes.get(key);
            if (consumers != null && item != null) {
                consumers.forEach((attributeName, consumer) -> {
                    var attributeValue = Optional.ofNullable(reader.getAttributeValue(null, attributeName));
                    attributeValue.ifPresent(v -> consumer.accept(item, v));
                });
            }
        }

        private boolean parseEndElement() {
            var nsTagName = toNsName(reader.getPrefix(), reader.getLocalName());
            var text = textBuilder.toString().trim();
            var elementFullPath = getElementFullPath();
            elementStack.removeLast();

            if (isChannelPart) {
                parseChannelCharacters(channel, nsTagName, elementFullPath, text);
            } else {
                parseItemCharacters(item, nsTagName, elementFullPath, text);
            }

            textBuilder.setLength(0);
            return isItem(nsTagName);
        }

        private void parseCharacters() {
            var text = reader.getText();
            if (text.isBlank()) {
                return;
            }
            textBuilder.append(text);
        }

        private void parseChannelCharacters(C channel, String nsTagName, String elementFullPath, String text) {
            if (channel == null || text.isEmpty()) {
                return;
            }
            channelTags.computeIfPresent(nsTagName, (k, f) -> { f.accept(channel, text); return f; });
            channelTags.computeIfPresent(elementFullPath, (k, f) -> { f.accept(channel, text); return f; });
        }

        private void parseItemCharacters(final I item, String nsTagName, String elementFullPath, final String text) {
            var builder = childNodeTextBuilder.remove(nsTagName);
            if (item == null || (text.isEmpty() && builder == null)) {
                return;
            }
            var textValue = (builder != null) ? builder.toString().trim() : text;
            itemTags.computeIfPresent(nsTagName, (k, f) -> { f.accept(item, textValue); return f; });

            itemTags.computeIfPresent(elementFullPath, (k, f) -> { f.accept(item, textValue); return f; });
        }

        private String toNsName(String prefix, String name) {
            return prefix.isEmpty() ? name : prefix + ":" + name;
        }

        private String toNamespacePrefix(String prefix) {
            return prefix == null || prefix.isEmpty() ? "xmlns" : "xmlns" + ":" + prefix;
        }

        private String getElementFullPath() {
            return "/" + String.join("/", elementStack);
        }
    }

    private HttpClient createHttpClient() {
        HttpClient client;
        try {
            var context = SSLContext.getInstance("TLSv1.3");
            context.init(null, null, null);

            var builder = HttpClient.newBuilder()
                    .sslContext(context)
                    .followRedirects(HttpClient.Redirect.ALWAYS);
            if (connectionTimeout.toMillis() > 0) {
                builder.connectTimeout(connectionTimeout);
            }
            client = builder.build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            var builder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS);
            if (connectionTimeout.toMillis() > 0) {
                builder.connectTimeout(connectionTimeout);
            }
            client = builder.build();
        }
        return client;
    }

}