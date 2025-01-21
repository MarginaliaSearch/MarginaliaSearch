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

import java.util.Objects;
import java.util.Optional;

/**
 * Class representing the Enclosure.
 */
public class Enclosure {
    private String url;
    private String type;
    private Long length;

    /**
     * Get the URL of enclosure.
     * @return url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the URL of the enclosure.
     * @param url URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get the type of enclosure.
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Set the type of the enclosure.
     * @param type type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the length of enclosure.
     * @return length
     */
    public Optional<Long> getLength() {
        return Optional.ofNullable(length);
    }

    /**
     * Set the length of the enclosure.
     * @param length length
     */
    public void setLength(Long length) {
        this.length = length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Enclosure enclosure = (Enclosure) o;
        return Objects.equals(getUrl(), enclosure.getUrl()) && Objects.equals(getType(), enclosure.getType()) && Objects.equals(getLength(), enclosure.getLength());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getType(), getLength());
    }

}
