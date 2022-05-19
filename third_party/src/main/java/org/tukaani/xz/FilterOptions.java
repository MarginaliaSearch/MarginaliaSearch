/*
 * FilterOptions
 *
 * Authors: Lasse Collin <lasse.collin@tukaani.org>
 *          Igor Pavlov <http://7-zip.org/>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.tukaani.xz;

import java.io.InputStream;
import java.io.IOException;

public abstract class FilterOptions implements Cloneable {
    public abstract int getEncoderMemoryUsage();
    public abstract FinishableOutputStream getOutputStream(
            FinishableOutputStream out);

    public abstract int getDecoderMemoryUsage();
    public abstract InputStream getInputStream(InputStream in)
            ;

    abstract FilterEncoder getFilterEncoder();

    FilterOptions() {}
}
