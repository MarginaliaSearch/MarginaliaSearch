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

import java.util.DoubleSummaryStatistics;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class AutoCloseDoubleStream extends AbstractAutoCloseStream<Double, DoubleStream> implements DoubleStream {

    AutoCloseDoubleStream(DoubleStream stream) {
        super(stream);
    }

    @Override
    public DoubleStream filter(DoublePredicate predicate) {
        return asAutoCloseStream(stream().filter(predicate));
    }

    @Override
    public DoubleStream map(DoubleUnaryOperator mapper) {
        return asAutoCloseStream(stream().map(mapper));
    }

    @Override
    public <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper) {
        return asAutoCloseStream(stream().mapToObj(mapper));
    }

    @Override
    public IntStream mapToInt(DoubleToIntFunction mapper) {
        return asAutoCloseStream(stream().mapToInt(mapper));
    }

    @Override
    public LongStream mapToLong(DoubleToLongFunction mapper) {
        return asAutoCloseStream(stream().mapToLong(mapper));
    }

    @Override
    public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
        return asAutoCloseStream(stream().flatMap(mapper));
    }

    @Override
    public DoubleStream distinct() {
        return asAutoCloseStream(stream().distinct());
    }

    @Override
    public DoubleStream sorted() {
        return asAutoCloseStream(stream().sorted());
    }

    @SuppressWarnings("java:S3864")
    @Override
    public DoubleStream peek(DoubleConsumer action) {
        return asAutoCloseStream(stream().peek(action));
    }

    @Override
    public DoubleStream limit(long maxSize) {
        return asAutoCloseStream(stream().limit(maxSize));
    }

    @Override
    public DoubleStream skip(long n) {
        return asAutoCloseStream(stream().skip(n));
    }

    @Override
    public void forEach(DoubleConsumer action) {
        autoClose(stream -> {
            stream.forEach(action);
            return null;
        });
    }

    @Override
    public void forEachOrdered(DoubleConsumer action) {
        autoClose(stream -> {
           stream.forEachOrdered(action);
           return null;
        });
    }

    @Override
    public double[] toArray() {
        return autoClose(DoubleStream::toArray);
    }

    @Override
    public double reduce(double identity, DoubleBinaryOperator op) {
        return autoClose(stream -> stream.reduce(identity, op));
    }

    @Override
    public OptionalDouble reduce(DoubleBinaryOperator op) {
        return autoClose(stream -> stream.reduce(op));
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        return autoClose(stream -> stream.collect(supplier, accumulator, combiner));
    }

    @Override
    public double sum() {
        return autoClose(DoubleStream::sum);
    }

    @Override
    public OptionalDouble min() {
        return autoClose(DoubleStream::min);
    }

    @Override
    public OptionalDouble max() {
        return autoClose(DoubleStream::max);
    }

    @Override
    public long count() {
        return autoClose(DoubleStream::count);
    }

    @Override
    public OptionalDouble average() {
        return autoClose(DoubleStream::average);
    }

    @Override
    public DoubleSummaryStatistics summaryStatistics() {
        return autoClose(DoubleStream::summaryStatistics);
    }

    @Override
    public boolean anyMatch(DoublePredicate predicate) {
        return autoClose(stream -> stream.anyMatch(predicate));
    }

    @Override
    public boolean allMatch(DoublePredicate predicate) {
        return autoClose(stream -> stream.allMatch(predicate));
    }

    @Override
    public boolean noneMatch(DoublePredicate predicate) {
        return autoClose(stream -> stream.noneMatch(predicate));
    }

    @Override
    public OptionalDouble findFirst() {
        return autoClose(DoubleStream::findFirst);
    }

    @Override
    public OptionalDouble findAny() {
        return autoClose(DoubleStream::findAny);
    }

    @Override
    public Stream<Double> boxed() {
        return asAutoCloseStream(stream().boxed());
    }

    @Override
    public DoubleStream sequential() {
        return asAutoCloseStream(stream().sequential());
    }

    @Override
    public DoubleStream parallel() {
        return asAutoCloseStream(stream().parallel());
    }

    @Override
    public PrimitiveIterator.OfDouble iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfDouble spliterator() {
        return stream().spliterator();
    }

    @Override
    public boolean isParallel() {
        return stream().isParallel();
    }

    @Override
    public DoubleStream unordered() {
        return asAutoCloseStream(stream().unordered());
    }

    @Override
    public DoubleStream onClose(Runnable closeHandler) {
        return asAutoCloseStream(stream().onClose(closeHandler));
    }
}
