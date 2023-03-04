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

public class RedirectEntry extends DirectoryEntry {

	final int redirectIndex;

	public RedirectEntry(int mimeType, char namespace, int revision,
			int redirectIndex, String url, String title, int urlListindex) {

		super(mimeType, namespace, revision, url, title, urlListindex);

		this.redirectIndex = redirectIndex;
	}

	public int getRedirectIndex() {
		return redirectIndex;
	}

}
