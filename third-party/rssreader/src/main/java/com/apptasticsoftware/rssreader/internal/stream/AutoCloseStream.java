/*
 * MIT License
 *
 * Copyright (c) 2024, Apptastic Software
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
package com.apptasticsoftware.rssreader.internal.stream;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * A Stream that automatically calls its {@link #close()} method after a terminating operation, such as limit(), forEach(), or collect(), has been executed.
 * <p>
 * This class is useful for working with streams that have resources that need to be closed, such as file streams or network connections.
 * <p>
 * If the {@link #close()} method is called manually, the stream will be closed and any subsequent operations will throw an {@link IllegalStateException}.
 * <p>
 * The {@link #iterator()} and {@link #spliterator()} methods are not supported by this class.
 */
@SuppressWarnings("javaarchitecture:S7027")
public class AutoCloseStream<T> extends AbstractAutoCloseStream<T, Stream<T>> implements Stream<T> {

    AutoCloseStream(Stream<T> stream) {
        super(stream);
    }

    /**
     * Creates a new AutoCloseStream from the given stream.
     * @param stream the stream to wrap
     * @return a new AutoCloseStream
     */
    public static <T> AutoCloseStream<T> of(Stream<T> stream) {
        Objects.requireNonNull(stream);
        return new AutoCloseStream<>(stream);
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return asAutoCloseStream(stream().filter(predicate));
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return asAutoCloseStream(stream().map(mapper));
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return asAutoCloseStream(stream().mapToInt(mapper));
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return asAutoCloseStream(stream().mapToLong(mapper));
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return asAutoCloseStream(stream().mapToDouble(mapper));
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return asAutoCloseStream(stream().flatMap(mapper));
    }

    @Override
        public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return asAutoCloseStream(stream().flatMapToInt(mapper));
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return asAutoCloseStream(stream().flatMapToLong(mapper));
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return asAutoCloseStream(stream().flatMapToDouble(mapper));
    }

    @Override
    public Stream<T> distinct() {
        return asAutoCloseStream(stream().distinct());
    }

    @Override
    public Stream<T> sorted() {
        return asAutoCloseStream(stream().sorted());
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return asAutoCloseStream(stream().sorted(comparator));
    }

    @SuppressWarnings("java:S3864")
    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        return asAutoCloseStream(stream().peek(action));
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return asAutoCloseStream(stream().limit(maxSize));
    }

    @Override
    public Stream<T> skip(long n) {
        return asAutoCloseStream(stream().skip(n));
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        autoClose(stream -> {
            stream.forEach(action);
            return null;
        });
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        autoClose(stream -> {
            stream.forEachOrdered(action);
            return null;
        });
    }

    @Override
    public Object[] toArray() {
        return autoClose(Stream::toArray);
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return autoClose(stream -> stream.toArray(generator));
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return autoClose(stream -> stream.reduce(identity, accumulator));
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return autoClose(stream -> stream.reduce(accumulator));
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return autoClose(stream -> stream.reduce(identity, accumulator, combiner));
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return autoClose(stream -> stream.collect(supplier, accumulator, combiner));
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return autoClose(stream -> stream.collect(collector));
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return autoClose(stream -> stream.min(comparator));
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return autoClose(stream -> stream.max(comparator));
    }

    @Override
    public long count() {
        return autoClose(Stream::count);
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return autoClose(stream -> stream.anyMatch(predicate));
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return autoClose(stream -> stream.allMatch(predicate));
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return autoClose(stream -> stream.noneMatch(predicate));
    }

    @Override
    public Optional<T> findFirst() {
        return autoClose(Stream::findFirst);
    }

    @Override
    public Optional<T> findAny() {
        return autoClose(Stream::findAny);
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return stream().spliterator();
    }

    @Override
    public boolean isParallel() {
        return stream().isParallel();
    }

    @Override
    public Stream<T> sequential() {
        return asAutoCloseStream(stream().sequential());
    }

    @Override
    public Stream<T> parallel() {
        return asAutoCloseStream(stream().parallel());
    }

    @Override
    public Stream<T> unordered() {
        return asAutoCloseStream(stream().unordered());
    }

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        return asAutoCloseStream(stream().onClose(closeHandler));
    }
}
