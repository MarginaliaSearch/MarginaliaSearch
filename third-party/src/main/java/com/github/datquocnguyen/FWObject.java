package com.github.datquocnguyen;

import java.util.Arrays;

/**
 * @author DatQuocNguyen
 * 
 */

/*
 * Define a 5-word/tag window object to capture the context surrounding a word
 */
public class FWObject
{
	public String[] context;
	private final static String[] contextPrototype;
	static {
		contextPrototype = new String[13];
		for (int i = 0; i < 10; i += 2) {
			contextPrototype[i] = "<W>";
			contextPrototype[i + 1] = "<T>";
		}
		contextPrototype[10] = "<SFX>";
		contextPrototype[11] = "<SFX>";
		contextPrototype[12] = "<SFX>";
	}
	public FWObject(boolean check)
	{
		// Previous2ndWord, Previous2ndTag, PreviousWord, PreviousTag, Word,
		// Tag, NextWord, NextTag, Next2ndWord, Next2ndTag, 2-chars suffix,
		// 3-char suffix, 4-char suffix
		if (check) {
			context = Arrays.copyOf(contextPrototype, 13);
		}
		else {
			context = new String[13];
		}
	}

	public void reset(boolean check) {
		if (check) {
			System.arraycopy(contextPrototype, 0, context, 0, 13);
		}
		else {
			Arrays.fill(context, null);
		}
	}
}
