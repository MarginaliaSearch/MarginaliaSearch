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

public class Utilities {
	
	// TODO: Write a binary search algorithm
	public static int binarySearch() {
		return -1;
	}
	
	public static int toTwoLittleEndianInteger(byte[] buffer) {
		if (buffer.length < 2) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
			return result;
		}
	}

	public static int toFourLittleEndianInteger(byte[] buffer) {
		if (buffer.length < 4) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
					| ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
			return result;
		}
	}

	public static long toEightLittleEndianInteger(byte[] buffer) {
		if (buffer.length < 8) {
			throw new OutOfMemoryError("buffer too small");
		} else {
			long result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
					| ((buffer[2] & 0xFF) << 16) | ((long) (buffer[3] & 0xFF) << 24L)
					| ((long) (buffer[4] & 0xFF) << 32L) | ((long) (buffer[5] & 0xFF) << 40L)
					| ((long) (buffer[6] & 0xFF) << 48L) | ((long) (buffer[7] & 0xFF) << 56L));
			return result;
		}
	}

	public static void skipFully(InputStream stream, long bytes) throws IOException {
		for (long i = stream.skip(bytes); i < bytes; i += stream.skip(bytes - i));
	}

}
