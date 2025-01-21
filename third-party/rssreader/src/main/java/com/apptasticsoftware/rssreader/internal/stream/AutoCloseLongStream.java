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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class AutoCloseLongStream extends AbstractAutoCloseStream<Long, LongStream> implements LongStream {

    AutoCloseLongStream(LongStream stream) {
        super(stream);
    }

    @Override
    public LongStream filter(LongPredicate predicate) {
        return asAutoCloseStream(stream().filter(predicate));
    }

    @Override
    public LongStream map(LongUnaryOperator mapper) {
        return asAutoCloseStream(stream().map(mapper));
    }

    @Override
    public <U> Stream<U> mapToObj(LongFunction<? extends U> mapper) {
        return asAutoCloseStream(stream().mapToObj(mapper));
    }

    @Override
    public IntStream mapToInt(LongToIntFunction mapper) {
        return asAutoCloseStream(stream().mapToInt(mapper));
    }

    @Override
    public DoubleStream mapToDouble(LongToDoubleFunction mapper) {
        return asAutoCloseStream(stream().mapToDouble(mapper));
    }

    @Override
    public LongStream flatMap(LongFunction<? extends LongStream> mapper) {
        return asAutoCloseStream(stream().flatMap(mapper));
    }

    @Override
    public LongStream distinct() {
        return asAutoCloseStream(stream().distinct());
    }

    @Override
    public LongStream sorted() {
        return asAutoCloseStream(stream().sorted());
    }

    @SuppressWarnings("java:S3864")
    @Override
    public LongStream peek(LongConsumer action) {
        return asAutoCloseStream(stream().peek(action));
    }

    @Override
    public LongStream limit(long maxSize) {
        return asAutoCloseStream(stream().limit(maxSize));
    }

    @Override
    public LongStream skip(long n) {
        return asAutoCloseStream(stream().skip(n));
    }

    @Override
    public void forEach(LongConsumer action) {
        autoClose(stream -> {
            stream.forEach(action);
            return null;
        });
    }

    @Override
    public void forEachOrdered(LongConsumer action) {
        autoClose(stream -> {
            stream.forEachOrdered(action);
            return null;
        });
    }

    @Override
    public long[] toArray() {
        return autoClose(LongStream::toArray);
    }

    @Override
    public long reduce(long identity, LongBinaryOperator op) {
        return autoClose(stream -> stream.reduce(identity, op));
    }

    @Override
    public OptionalLong reduce(LongBinaryOperator op) {
        return autoClose(stream -> stream.reduce(op));
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        return autoClose(stream -> stream.collect(supplier, accumulator, combiner));
    }

    @Override
    public long sum() {
        return autoClose(LongStream::sum);
    }

    @Override
    public OptionalLong min() {
        return autoClose(LongStream::min);
    }

    @Override
    public OptionalLong max() {
        return autoClose(LongStream::max);
    }

    @Override
    public long count() {
        return autoClose(LongStream::count);
    }

    @Override
    public OptionalDouble average() {
        return autoClose(LongStream::average);
    }

    @Override
    public LongSummaryStatistics summaryStatistics() {
        return autoClose(LongStream::summaryStatistics);
    }

    @Override
    public boolean anyMatch(LongPredicate predicate) {
        return autoClose(stream -> stream.anyMatch(predicate));
    }

    @Override
    public boolean allMatch(LongPredicate predicate) {
        return autoClose(stream -> stream.allMatch(predicate));
    }

    @Override
    public boolean noneMatch(LongPredicate predicate) {
        return autoClose(stream -> stream.noneMatch(predicate));
    }

    @Override
    public OptionalLong findFirst() {
        return autoClose(LongStream::findFirst);
    }

    @Override
    public OptionalLong findAny() {
        return autoClose(LongStream::findAny);
    }

    @Override
    public DoubleStream asDoubleStream() {
        return asAutoCloseStream(stream().asDoubleStream());
    }

    @Override
    public Stream<Long> boxed() {
        return asAutoCloseStream(stream().boxed());
    }

    @Override
    public LongStream sequential() {
        return asAutoCloseStream(stream().sequential());
    }

    @Override
    public LongStream parallel() {
        return asAutoCloseStream(stream().parallel());
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfLong spliterator() {
        return stream().spliterator();
    }

    @Override
    public boolean isParallel() {
        return stream().isParallel();
    }

    @Override
    public LongStream unordered() {
        return asAutoCloseStream(stream().unordered());
    }

    @Override
    public LongStream onClose(Runnable closeHandler) {
        return asAutoCloseStream(stream().onClose(closeHandler));
    }

}
