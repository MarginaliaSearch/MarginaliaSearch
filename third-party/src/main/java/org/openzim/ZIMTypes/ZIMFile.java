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

package org.openzim.ZIMTypes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.*;

import org.openzim.util.RandomAcessFileZIMInputStream;

/**
 * @author Arunesh Mathur
 * 
 *         A ZIM file implementation that stores the Header and the MIMETypeList
 * 
 */
public class ZIMFile extends File {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Header mHeader;

	private final Map<Integer, String> mMIMETypeList = new HashMap<>(); // Can be removed if not needed

	public ZIMFile(String path) {
		super(path);

		try {
			readHeader();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void readHeader() throws FileNotFoundException {

		// Helpers
		int len = 0;
		StringBuffer mimeBuffer = null;

		// The byte[] that will help us in reading bytes out of the file
		byte[] buffer = new byte[16];

		// Check whether the file exists
		if (!(this.exists())) {
			throw new FileNotFoundException(
					"The file that you specified was not found.");
		}

		// The reader that will be used to read contents from the file

		RandomAcessFileZIMInputStream reader = new RandomAcessFileZIMInputStream(
				new RandomAccessFile(this, "r"));

		// The ZIM file header
		mHeader = new Header();

		// Read the contents of the header
		try {
			mHeader.magicNumber = reader.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.magicNumber);

			mHeader.version = reader.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.version);

			mHeader.uuid = reader.readSixteenLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.uuid); reader.read(buffer, 0, 4);

			mHeader.articleCount = reader
					.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.articleCount);

			mHeader.clusterCount = reader
					.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.clusterCount);

			mHeader.urlPtrPos = reader.readEightLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.urlPtrPos);

			mHeader.titlePtrPos = reader
					.readEightLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.titlePtrPos);

			mHeader.clusterPtrPos = reader
					.readEightLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.clusterPtrPos);

			mHeader.mimeListPos = reader
					.readEightLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.mimeListPos);

			mHeader.mainPage = reader.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.mainPage);

			mHeader.layoutPage = reader.readFourLittleEndianBytesValue(buffer);
			// System.out.println(mHeader.layoutPage);

			reader.seek(mHeader.mimeListPos);
			// Initialise the MIMETypeList
			while (true) {
				reader.read(buffer, 0, 1);
				len = 0;
				mimeBuffer = new StringBuffer();
				while (buffer[0] != '\0') {
					mimeBuffer.append((char) buffer[0]);
					reader.read(buffer, 0, 1);
					len++;
				}
				if (len == 0) {
					break;
				}
				mMIMETypeList.put(mMIMETypeList.size(), mimeBuffer.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getVersion() {
		return mHeader.version;
	}

	public int getUuid() {
		return mHeader.uuid;
	}

	public int getArticleCount() {
		return mHeader.articleCount;
	}

	public int getClusterCount() {
		return mHeader.clusterCount;
	}

	public long getUrlPtrPos() {
		return mHeader.urlPtrPos;
	}

	public long getTitlePtrPos() {
		return mHeader.titlePtrPos;
	}

	public long getClusterPtrPos() {
		return mHeader.clusterPtrPos;
	}

	public String getMIMEType(int mimeNumber) {
		return mMIMETypeList.get(mimeNumber);
	}
	public Map<Integer, String> getMIMETypes() {
		return Collections.unmodifiableMap(mMIMETypeList);
	}

	public long getHeaderSize() {
		return mHeader.mimeListPos;
	}

	public int getMainPage() {
		return mHeader.mainPage;
	}

	public int getLayoutPage() {
		return mHeader.layoutPage;
	}

	public static class Header {
		int magicNumber;
		int version;
		int uuid;
		int articleCount;
		int clusterCount;
		long urlPtrPos;
		long titlePtrPos;
		long clusterPtrPos;
		long mimeListPos;
		int mainPage;
		int layoutPage;
	}

}
