package nu.marginalia.integration.stackexchange.xml;

import lombok.SneakyThrows;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.XMLEvent;
import java.util.Iterator;
import java.util.function.Function;

class StackExchangeXmlIterator<T> implements Iterator<T> {
    private T next = null;

    private final XmlEventReaderSource readerSource;
    private final XMLEventReader xmlReader;
    private final Function<XMLEvent, T> parser;

    protected StackExchangeXmlIterator(XmlEventReaderSource readerSource,
                                       Function<XMLEvent, T> parser
    ) {
        this.readerSource = readerSource;
        this.xmlReader = readerSource.reader();
        this.parser = parser;
    }

    @SneakyThrows
    @Override
    public boolean hasNext() {
        if (next != null)
            return true;

        while (xmlReader.hasNext()) {
            XMLEvent event = xmlReader.nextEvent();

            if (!event.isStartElement())
                continue;

            next = parser.apply(event);

            if (next != null)
                return true;
        }

        readerSource.close();

        return false;
    }

    @Override
    public T next() {
        if (hasNext()) {
            var ret = next;
            next = null;
            return ret;
        }

        throw new IllegalStateException("No more posts");
    }
}
