/**
 * XZ data compression support.
 * <p>
 * In the (very) long term, this aims to be a complete implementation of
 * XZ data compression in Java. Currently only streamed decompression is
 * supported.
 * <p>
 * For the latest source code, see the
 * <a href="http://tukaani.org/xz/java.html">home page of XZ in Java</a>.
 *
 * <h3>Decompression notes</h3>
 *
 * If you are decompressing complete files and your application knows
 * exactly how much uncompressed data there should be, it is still good
 * to try reading one more byte by calling <code>read()</code> and checking
 * that it returns <code>-1</code>. This way the decompressor will parse the
 * file footers and verify the integrity checks, giving the caller more
 * confidence that the uncompressed data is valid. (This advice seems to
 * apply to <code>java.util.zip.GZIPInputStream</code> too.)
 */
package org.tukaani.xz;
