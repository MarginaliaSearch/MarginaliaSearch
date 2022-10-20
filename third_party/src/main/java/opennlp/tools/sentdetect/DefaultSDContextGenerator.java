/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentdetect;

import opennlp.tools.util.StringUtil;

import java.util.*;


// https://opennlp.apache.org/


// This code is hot patched to increase the default buffer size, which is sub-optimal in
// the released version


/**
 * Generate event contexts for maxent decisions for sentence detection.
 *
 */
public class DefaultSDContextGenerator implements SDContextGenerator {

    /**
     * String buffer for generating features.
     */
    protected StringBuilder buf;

    /**
     * List for holding features as they are generated.
     */
    protected List<String> collectFeats;

    private Set<String> inducedAbbreviations;

    private Set<Character> eosCharacters;

    /**
     * Creates a new <code>SDContextGenerator</code> instance with
     * no induced abbreviations.
     *
     * @param eosCharacters
     */
    public DefaultSDContextGenerator(char[] eosCharacters) {
        this(Collections.emptySet(), eosCharacters);
    }

    /**
     * Creates a new <code>SDContextGenerator</code> instance which uses
     * the set of induced abbreviations.
     *
     * @param inducedAbbreviations a <code>Set</code> of Strings
     *     representing induced abbreviations in the training data.
     *     Example: &quot;Mr.&quot;
     *
     * @param eosCharacters
     */
    public DefaultSDContextGenerator(Set<String> inducedAbbreviations, char[] eosCharacters) {
        this.inducedAbbreviations = inducedAbbreviations;
        this.eosCharacters = new HashSet<>();
        for (char eosChar: eosCharacters) {
            this.eosCharacters.add(eosChar);
        }
        buf = new StringBuilder(128);
        collectFeats = new ArrayList<>();
    }

    private static String escapeChar(Character c) {
        if (c == '\n') {
            return "<LF>";
        }

        if (c == '\r') {
            return "<CR>";
        }

        return new String(new char[]{c});
    }

    /* (non-Javadoc)
     * @see opennlp.tools.sentdetect.SDContextGenerator#getContext(java.lang.CharSequence, int)
     */
    public String[] getContext(CharSequence sb, int position) {

        /*
         * String preceding the eos character in the eos token.
         */
        String prefix;

        /*
         * Space delimited token preceding token containing eos character.
         */
        String previous;

        /*
         * String following the eos character in the eos token.
         */
        String suffix;

        /*
         * Space delimited token following token containing eos character.
         */
        String next;

        int lastIndex = sb.length() - 1;
        { // compute space previous and space next features.
            if (position > 0 && StringUtil.isWhitespace(sb.charAt(position - 1)))
                collectFeats.add("sp");
            if (position < lastIndex && StringUtil.isWhitespace(sb.charAt(position + 1)))
                collectFeats.add("sn");
            collectFeats.add("eos=" + escapeChar(sb.charAt(position)));
        }
        int prefixStart = previousSpaceIndex(sb, position);

        int c = position;
        { ///assign prefix, stop if you run into a period though otherwise stop at space
            while (--c > prefixStart) {
                if (eosCharacters.contains(sb.charAt(c))) {
                    prefixStart = c;
                    c++; // this gets us out of while loop.
                }
            }
            prefix = String.valueOf(sb.subSequence(prefixStart, position)).trim();
        }
        int prevStart = previousSpaceIndex(sb, prefixStart);
        previous = String.valueOf(sb.subSequence(prevStart, prefixStart)).trim();

        int suffixEnd = nextSpaceIndex(sb, position, lastIndex);
        {
            c = position;
            while (++c < suffixEnd) {
                if (eosCharacters.contains(sb.charAt(c))) {
                    suffixEnd = c;
                    c--; // this gets us out of while loop.
                }
            }
        }
        int nextEnd = nextSpaceIndex(sb, suffixEnd + 1, lastIndex + 1);
        if (position == lastIndex) {
            suffix = "";
            next = "";
        }
        else {
            suffix = String.valueOf(sb.subSequence(position + 1, suffixEnd)).trim();
            next = String.valueOf(sb.subSequence(suffixEnd + 1, nextEnd)).trim();
        }

        collectFeatures(prefix,suffix,previous,next, sb.charAt(position));

        String[] context = new String[collectFeats.size()];
        context = collectFeats.toArray(context);
        collectFeats.clear();
        return context;
    }

    /**
     * Determines some of the features for the sentence detector and adds them to list features.
     *
     * @param prefix String preceding the eos character in the eos token.
     * @param suffix String following the eos character in the eos token.
     * @param previous Space delimited token preceding token containing eos character.
     * @param next Space delimited token following token containing eos character.
     *
     * @deprecated use {@link #collectFeatures(String, String, String, String, Character)} instead.
     */
    protected void collectFeatures(String prefix, String suffix, String previous, String next) {
        collectFeatures(prefix, suffix, previous, next, null);
    }

    /**
     * Determines some of the features for the sentence detector and adds them to list features.
     *
     * @param prefix String preceding the eos character in the eos token.
     * @param suffix String following the eos character in the eos token.
     * @param previous Space delimited token preceding token containing eos character.
     * @param next Space delimited token following token containing eos character.
     * @param eosChar the EOS character been analyzed
     */
    protected void collectFeatures(String prefix, String suffix, String previous,
                                   String next, Character eosChar) {
        buf.append("x=");
        buf.append(prefix);
        collectFeats.add(buf.toString());
        buf.setLength(0);
        if (!prefix.equals("")) {
            collectFeats.add(Integer.toString(prefix.length()));
            if (isFirstUpper(prefix)) {
                collectFeats.add("xcap");
            }
            if (eosChar != null && inducedAbbreviations.contains(prefix + eosChar)) {
                collectFeats.add("xabbrev");
            }
        }

        buf.append("v=");
        buf.append(previous);
        collectFeats.add(buf.toString());

        buf.setLength(0);
        if (!previous.equals("")) {
            if (isFirstUpper(previous)) {
                collectFeats.add("vcap");
            }
            if (inducedAbbreviations.contains(previous)) {
                collectFeats.add("vabbrev");
            }
        }

        buf.append("s=");
        buf.append(suffix);
        collectFeats.add(buf.toString());
        buf.setLength(0);
        if (!suffix.equals("")) {
            if (isFirstUpper(suffix)) {
                collectFeats.add("scap");
            }
            if (inducedAbbreviations.contains(suffix)) {
                collectFeats.add("sabbrev");
            }
        }

        buf.append("n=");
        buf.append(next);
        collectFeats.add(buf.toString());
        buf.setLength(0);
        if (!next.equals("")) {
            if (isFirstUpper(next)) {
                collectFeats.add("ncap");
            }
            if (inducedAbbreviations.contains(next)) {
                collectFeats.add("nabbrev");
            }
        }
    }

    private static boolean isFirstUpper(String s) {
        return Character.isUpperCase(s.charAt(0));
    }

    /**
     * Finds the index of the nearest space before a specified index which is not itself preceded by a space.
     *
     * @param sb   The string buffer which contains the text being examined.
     * @param seek The index to begin searching from.
     * @return The index which contains the nearest space.
     */
    private static int previousSpaceIndex(CharSequence sb, int seek) {
        seek--;
        while (seek > 0 && !StringUtil.isWhitespace(sb.charAt(seek))) {
            seek--;
        }
        if (seek > 0 && StringUtil.isWhitespace(sb.charAt(seek))) {
            while (seek > 0 && StringUtil.isWhitespace(sb.charAt(seek - 1)))
                seek--;
            return seek;
        }
        return 0;
    }

    /**
     * Finds the index of the nearest space after a specified index.
     *
     * @param sb The string buffer which contains the text being examined.
     * @param seek The index to begin searching from.
     * @param lastIndex The highest index of the StringBuffer sb.
     * @return The index which contains the nearest space.
     */
    private static int nextSpaceIndex(CharSequence sb, int seek, int lastIndex) {
        seek++;
        char c;
        while (seek < lastIndex) {
            c = sb.charAt(seek);
            if (StringUtil.isWhitespace(c)) {
                while (sb.length() > seek + 1 && StringUtil.isWhitespace(sb.charAt(seek + 1)))
                    seek++;
                return seek;
            }
            seek++;
        }
        return lastIndex;
    }
}
