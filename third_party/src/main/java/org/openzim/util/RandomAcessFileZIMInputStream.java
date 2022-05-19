/*
 * Copyright (C) 2011 Arunesh Mathur
 * 
 * This file is a part of zimreader-java.
 *
 * zimreader-java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3.0 as 
 * published by the Free Software Foundation.
 *
 * zimreader-java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with zimreader-java.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openzim.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * This is an implementation of RandomAccessFile to ensure that it is an
 * InputStream as well, specifically designed for reading a ZIM file. Ad-Hoc
 * implementation, can be improved.
 * 
 * @author Arunesh Mathur <aruneshmathur1990 at gmail.com>
 */

public class RandomAcessFileZIMInputStream extends InputStream {

	private RandomAccessFile mRAFReader;

	private long mMarked = -1;

	public RandomAcessFileZIMInputStream(RandomAccessFile reader) {
		this.mRAFReader = reader;
	}

	// TODO: Remove the parameter buffer
	public int readTwoLittleEndianBytesValue(byte[] buffer) throws IOException {
		if (buffer.length < 2) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			mRAFReader.read(buffer, 0, 2);
			return Utilities.toTwoLittleEndianInteger(buffer);
		}
	}

	// TODO: Remove the parameter buffer
	public int readFourLittleEndianBytesValue(byte[] buffer) throws IOException {
		if (buffer.length < 4) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			mRAFReader.read(buffer, 0, 4);
			return Utilities.toFourLittleEndianInteger(buffer);
		}
	}

	// TODO: Remove the parameter buffer
	public long readEightLittleEndianBytesValue(byte[] buffer)
			throws IOException {
		if (buffer.length < 8) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			mRAFReader.read(buffer, 0, 8);
			return Utilities.toEightLittleEndianInteger(buffer);
		}
	}

	// TODO: Remove the parameter buffer
	public int readSixteenLittleEndianBytesValue(byte[] buffer)
			throws IOException {
		if (buffer.length < 16) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			mRAFReader.read(buffer, 0, 16);
			return Utilities.toSixteenLittleEndianInteger(buffer);
		}
	}

	// Reads characters from the current position into a String and stops when a
	// '\0' is encountered
	public String readString() throws IOException {
		StringBuilder sb = new StringBuilder();
		/*
		 * int i; byte[] buffer = new byte[100]; while (true) {
		 * mRAFReader.read(buffer); for (i = 0; i < buffer.length; i++) { if
		 * (buffer[i] == '\0') { break; } sb.append((char) buffer[i]); } if (i
		 * != buffer.length) break; } return sb.toString();
		 */
		byte[] buffer = new byte[64];
		for (;;) {
			mRAFReader.readFully(buffer);
			for (int i = 0; i < buffer.length; i++) {
				if (buffer[i] == 0) {
					sb.append(new String(buffer, 0, i));
					mRAFReader.seek(mRAFReader.getFilePointer() + 1 + (i - buffer.length));
					return sb.toString();
				}
			}
			sb.append(new String(buffer));
		}

	}

	@Override
	public int read() throws IOException {
		return mRAFReader.read();
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		return mRAFReader.read(b, off, len);
	}


	public RandomAccessFile getRandomAccessFile() {
		return mRAFReader;
	}

	public void seek(long pos) throws IOException {
		if (pos < 0) {
			System.out.println(pos);
		}
		mRAFReader.seek(pos);
	}

	public long getFilePointer() throws IOException {
		return mRAFReader.getFilePointer();
	}

	public void mark() throws IOException {
		this.mMarked = mRAFReader.getFilePointer();
	}

	public void reset() throws IOException {
		if (this.mMarked == -1) {
			return;
		} else {
			mRAFReader.seek(mMarked);
			this.mMarked = -1;
		}
	}
}
