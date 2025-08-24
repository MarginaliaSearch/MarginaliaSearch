package com.github.datquocnguyen;

import java.util.HashMap;

/** GPLv3
 * @author DatQuocNguyen
 * 
 */
public class InitialTagger
{
	static public boolean jj1(String s) {
		int idx = s.indexOf('-');
		while (idx >= 0) {
			if (idx > 0 && isDigit(s.charAt(idx-1)))
				return true;
			if (idx+1 < s.length() && isDigit(s.charAt(idx+1)))
				return true;

			idx = s.indexOf('-', idx+1);
		}
		return false;
	}

	static public boolean nn(String s) {
		if (s.endsWith("ness"))
			return true;
		if (s.endsWith("ment"))
			return true;
		if (s.endsWith("ship"))
			return true;
		if (s.startsWith("Ex"))
			return true;
		if (s.startsWith("ex"))
			return true;
		if (s.startsWith("Self-"))
			return true;
		if (s.startsWith("self-"))
			return true;

		return false;
	}
	static public boolean jj2(String s) {
		if (s.startsWith("Inter"))
			return true;
		if (s.startsWith("inter"))
			return true;
		if (s.startsWith("Dis"))
			return true;
		if (s.startsWith("dis"))
			return true;
		if (s.startsWith("Anti"))
			return true;
		if (s.startsWith("anti"))
			return true;

		return false;
	}
	static public boolean jj3(String s) {
		if (s.contains("-"))
			return true;
		if (s.endsWith("ful"))
			return true;
		if (s.endsWith("ous"))
			return true;
		if (s.endsWith("ble"))
			return true;
		if (s.endsWith("ic"))
			return true;
		if (s.endsWith("ive"))
			return true;
		if (s.endsWith("est"))
			return true;
		if (s.endsWith("able"))
			return true;
		if (s.endsWith("al"))
			return true;

		return false;
	}
	static public boolean url(String s) {
		int pointIdx = s.indexOf('.');
		return pointIdx >= 0 && pointIdx != s.length()-1;
	}
	static public boolean cd(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (isDigit(c)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	static public boolean rb(String s) {
		return s.endsWith("ly");
	}
	static public boolean vbn(String s) {
		return s.endsWith("vbn");
	}
	static public boolean vbg(String s) {
		return s.endsWith("vbg");
	}

	static public boolean nns(String s) {
		return Character.isLowerCase(s.charAt(0)) && s.endsWith("s");
	}

    public static String[] InitTagger4Sentence(
            HashMap<String, String> DICT, String[] sentence)
    {
        String[] wordtags = new String[sentence.length];

        for (int i = 0; i < sentence.length; i++) {
            wordtags[i] = getTagForWord(DICT, sentence[i]);
        }
        return wordtags;
    }


    private static String getTagForWord(HashMap<String, String> DICT, String word) {
        if ("[]()<>!".contains(word)) {
            return "?";
        }

        String tag = "";
        String lowerW = word.toLowerCase();
        if (DICT.containsKey(word))
            tag = DICT.get(word);
        else if (DICT.containsKey(lowerW))
            tag = DICT.get(lowerW);
        else {
            if (cd(word)) {
                tag = DICT.get("TAG4UNKN-NUM");
            }
            else {
                String suffixL2 = null, suffixL3 = null, suffixL4 = null, suffixL5 = null;

                int wordLength = word.length();
                if (wordLength >= 4) {
                    suffixL2 = ".*" + word.substring(wordLength - 2);
                    suffixL3 = ".*" + word.substring(wordLength - 3);
                }
                if (wordLength >= 5) {
                    suffixL4 = ".*" + word.substring(wordLength - 4);
                }
                if (wordLength >= 6) {
                    suffixL5 = ".*" + word.substring(wordLength - 5);
                }

                if (DICT.containsKey(suffixL5)) {
                    tag = DICT.get(suffixL5);
                }
                else if (DICT.containsKey(suffixL4)) {
                    tag = DICT.get(suffixL4);
                }
                else if (DICT.containsKey(suffixL3)) {
                    tag = DICT.get(suffixL3);
                }
                else if (DICT.containsKey(suffixL2)) {
                    tag = DICT.get(suffixL2);
                }
                else if (Character.isUpperCase(word.codePointAt(0)))
                    tag = DICT.get("TAG4UNKN-CAPITAL");
                else
                    tag = DICT.get("TAG4UNKN-WORD");
            }
        }

        return tag;
    }

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
		if (word.contains("\"") || word.contains("“") || word.contains("”"))
			return DICT.get("''");

		if ("[]()<>!".contains(word)) {
			return "?";
		}

		if (DICT.containsKey(word))
			return DICT.get(word);
		String lowerW = word.toLowerCase();
		if (DICT.containsKey(lowerW))
			return DICT.get(lowerW);
		if (jj1(word))
			return "JJ";
		if (url(word))
			return "NN";
		if (cd(word))
			return "CD";
		if (nn(word))
			return "NN";
		if (nns(word))
			return "NNS";
		if (Character.isUpperCase(word.charAt(0)))
			return "NNP";
		if (jj2(word))
			return "JJ";
		if (vbg(word))
			return "VBG";
		if (vbn(word))
			return "VBN";
		if (jj3(word))
			return "JJ";
		if (rb(word))
			return "RB";

		return "NN";
	}


}
