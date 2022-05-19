/*
 * XZIOException
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

/**
 * Generic IOException specific to this package.
 * All IOExceptions thrown by this package are extended from XZIOException.
 * This way it is easier to distinguish exceptions thrown by the XZ code
 * from other IOExceptions.
 */
public class XZIOException extends java.io.IOException {
    private static final long serialVersionUID = 3L;

    public XZIOException() {
        super();
    }

    public XZIOException(String s) {
        super(s);
    }
}
