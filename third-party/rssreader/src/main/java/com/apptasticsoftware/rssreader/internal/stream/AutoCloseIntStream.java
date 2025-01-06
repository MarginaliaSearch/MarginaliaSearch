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

public class AutoCloseIntStream extends AbstractAutoCloseStream<Integer, IntStream> implements IntStream {

    AutoCloseIntStream(IntStream stream) {
        super(stream);
    }

    @Override
    public IntStream filter(IntPredicate predicate) {
        return asAutoCloseStream(stream().filter(predicate));
    }

    @Override
    public IntStream map(IntUnaryOperator mapper) {
        return asAutoCloseStream(stream().map(mapper));
    }

    @Override
    public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
        return asAutoCloseStream(stream().mapToObj(mapper));
    }

    @Override
    public LongStream mapToLong(IntToLongFunction mapper) {
        return asAutoCloseStream(stream().mapToLong(mapper));
    }

    @Override
    public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
        return asAutoCloseStream(stream().mapToDouble(mapper));
    }

    @Override
    public IntStream flatMap(IntFunction<? extends IntStream> mapper) {
        return asAutoCloseStream(stream().flatMap(mapper));
    }

    @Override
    public IntStream distinct() {
        return asAutoCloseStream(stream().distinct());
    }

    @Override
    public IntStream sorted() {
        return asAutoCloseStream(stream().sorted());
    }

    @SuppressWarnings("java:S3864")
    @Override
    public IntStream peek(IntConsumer action) {
        return asAutoCloseStream(stream().peek(action));
    }

    @Override
    public IntStream limit(long maxSize) {
        return asAutoCloseStream(stream().limit(maxSize));
    }

    @Override
    public IntStream skip(long n) {
        return asAutoCloseStream(stream().skip(n));
    }

    @Override
    public void forEach(IntConsumer action) {
        autoClose(stream -> {
            stream.forEach(action);
            return null;
        });
    }

    @Override
    public void forEachOrdered(IntConsumer action) {
        autoClose(stream -> {
            stream.forEachOrdered(action);
            return null;
        });
    }

    @Override
    public int[] toArray() {
        return autoClose(IntStream::toArray);
    }

    @Override
    public int reduce(int identity, IntBinaryOperator op) {
        return autoClose(stream -> stream.reduce(identity, op));
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator op) {
        return autoClose(stream -> stream.reduce(op));
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        return autoClose(stream -> stream.collect(supplier, accumulator, combiner));
    }

    @Override
    public int sum() {
        return autoClose(IntStream::sum);
    }

    @Override
    public OptionalInt min() {
        return autoClose(IntStream::min);
    }

    @Override
    public OptionalInt max() {
        return autoClose(IntStream::max);
    }

    @Override
    public long count() {
        return autoClose(IntStream::count);
    }

    @Override
    public OptionalDouble average() {
        return autoClose(IntStream::average);
    }

    @Override
    public IntSummaryStatistics summaryStatistics() {
        return autoClose(IntStream::summaryStatistics);
    }

    @Override
    public boolean anyMatch(IntPredicate predicate) {
        return autoClose(stream -> stream.anyMatch(predicate));
    }

    @Override
    public boolean allMatch(IntPredicate predicate) {
        return autoClose(stream -> stream.allMatch(predicate));
    }

    @Override
    public boolean noneMatch(IntPredicate predicate) {
        return autoClose(stream -> stream.noneMatch(predicate));
    }

    @Override
    public OptionalInt findFirst() {
        return autoClose(IntStream::findFirst);
    }

    @Override
    public OptionalInt findAny() {
        return autoClose(IntStream::findAny);
    }

    @Override
    public LongStream asLongStream() {
        return asAutoCloseStream(stream().asLongStream());
    }

    @Override
    public DoubleStream asDoubleStream() {
        return asAutoCloseStream(stream().asDoubleStream());
    }

    @Override
    public Stream<Integer> boxed() {
        return asAutoCloseStream(stream().boxed());
    }

    @Override
    public IntStream sequential() {
        return asAutoCloseStream(stream().sequential());
    }

    @Override
    public IntStream parallel() {
        return asAutoCloseStream(stream().parallel());
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfInt spliterator() {
        return stream().spliterator();
    }

    @Override
    public boolean isParallel() {
        return stream().isParallel();
    }

    @Override
    public IntStream unordered() {
        return asAutoCloseStream(stream().unordered());
    }

    @Override
    public IntStream onClose(Runnable closeHandler) {
        return asAutoCloseStream(stream().onClose(closeHandler));
    }
}
