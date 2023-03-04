package com.github.datquocnguyen;

/**
 * @author DatQuocNguyen
 * 
 */
public class WordTag
{
	public final String word;
	public final String tag;

	public WordTag(String iword, String itag)
	{
		word = iword;
		tag = itag;
	}

	public String getTag() {
		return tag;
	}
}
