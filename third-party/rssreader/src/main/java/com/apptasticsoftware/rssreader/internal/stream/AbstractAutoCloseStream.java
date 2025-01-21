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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.*;

@SuppressWarnings("javaarchitecture:S7027")
public class AbstractAutoCloseStream<T, S extends BaseStream<T, S>> implements AutoCloseable {
    private final S stream;
    private final AtomicBoolean isClosed;

    AbstractAutoCloseStream(S stream) {
        this.stream = Objects.requireNonNull(stream);
        this.isClosed = new AtomicBoolean();
    }

    protected S stream() {
        return stream;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false,true)) {
            stream().close();
        }
    }

    <R> R autoClose(Function<S, R> function) {
        try (S s = stream()) {
            return function.apply(s);
        }
    }

    <U> Stream<U> asAutoCloseStream(Stream<U> stream) {
        return asAutoCloseStream(stream, AutoCloseStream::new);
    }

    IntStream asAutoCloseStream(IntStream stream) {
        return asAutoCloseStream(stream, AutoCloseIntStream::new);
    }

    LongStream asAutoCloseStream(LongStream stream) {
        return asAutoCloseStream(stream, AutoCloseLongStream::new);
    }

    DoubleStream asAutoCloseStream(DoubleStream stream) {
        return asAutoCloseStream(stream, AutoCloseDoubleStream::new);
    }

    private <U> U asAutoCloseStream(U stream, UnaryOperator<U> wrapper) {
        if (stream instanceof AbstractAutoCloseStream) {
            return stream;
        }
        return wrapper.apply(stream);
    }

}
