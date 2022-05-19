package com.github.datquocnguyen;

import java.util.HashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** GPLv3
 * @author DatQuocNguyen
 * 
 */
public class InitialTagger
{
	private static final Pattern QUOTATION = Pattern.compile("(“)|(”)|(\")");

	private static final Predicate<String> CD = Pattern.compile("[0-9]+").asPredicate();
	private static final Predicate<String> URL = Pattern.compile("[A-Za-z]\\w*(\\.[A-Za-z]\\w+)+").asPredicate();
	private static final Predicate<String> JJ1 = Pattern.compile("([0-9]+-)|(-[0-9]+)").asPredicate();
	private static final Predicate<String> JJ2 = Pattern.compile("(^[Ii]nter.*)|(^[nN]on.*)|(^[Dd]is.*)|(^[Aa]nti.*)").asPredicate();
	private static final Predicate<String> JJ3 = Pattern.compile("(.*ful$)|(.*ous$)|(.*ble$)|(.*ic$)|(.*ive$)|(.*est$)|(.*able$)|(.*al$)").asPredicate();
	private static final Predicate<String> NN = Pattern.compile("(.*ness$)|(.*ment$)|(.*ship$)|(^[Ee]x-.*)|(^[Ss]elf-.*)").asPredicate();
	private static final Predicate<String> NNS = Pattern.compile(".*s$").asPredicate();
	private static final Predicate<String> VBG = Pattern.compile(".*ing$").asPredicate();
	private static final Predicate<String> VBN = Pattern.compile(".*ed$").asPredicate();
	private static final Predicate<String> RB = Pattern.compile(".*ly$").asPredicate();

	public static String[] EnInitTagger4Sentence(
		HashMap<String, String> DICT, String[] sentence)
	{
		String[] wordtags = new String[sentence.length];

		for (int i = 0; i < sentence.length; i++) {
			wordtags[i] = getTagForWordEn(DICT, sentence[i]);
		}
		return wordtags;
	}

	private static String getTagForWordEn(HashMap<String, String> DICT, String word) {
		if (QUOTATION.matcher(word).find()) {
			return DICT.get("''");
		}
		if ("[]()<>!".contains(word)) {
			return "?";
		}

		if (DICT.containsKey(word))
			return DICT.get(word);
		String lowerW = word.toLowerCase();
		if (DICT.containsKey(lowerW))
			return DICT.get(lowerW);
		if (JJ1.test(word))
			return "JJ";
		if (URL.test(word))
			return "NN";
		if (CD.test(word))
			return "CD";
		if (NN.test(word))
			return "NN";
		if (NNS.test(word)
				&& Character.isLowerCase(word.charAt(0)))
			return "NNS";
		if (Character.isUpperCase(word.charAt(0)))
			return "NNP";
		if (JJ2.test(word))
			return "JJ";
		if (VBG.test(word))
			return "VBG";
		if (VBN.test(word))
			return "VBN";
		if (word.contains("-") || JJ3.test(word))
			return "JJ";
		if (RB.test(word))
			return "RB";

		return "NN";
	}


}
