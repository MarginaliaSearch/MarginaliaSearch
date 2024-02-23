package nu.marginalia.gregex;

import org.intellij.lang.annotations.Language;

import java.util.regex.Pattern;


public class GuardedRegexFactory {

    // Regular expressions are slow, even compiled ones. Guarding them with startsWith, or even contains
    // is something like an order of magnitude faster. This matters a lot in hot code.

    public static GuardedRegex startsWith(String prefix, @Language("RegExp") String regex) {
        return new GuardedRegexStartsWith(prefix, regex);
    }
    public static GuardedRegex endsWith(String suffix, @Language("RegExp") String regex) {
        return new GuardedRegexEndsWith(suffix, regex);
    }
    public static GuardedRegex contains(String substring, @Language("RegExp") String regex) {
        return new GuardedRegexContains(substring, regex);
    }

    private record GuardedRegexContains(String contains, Pattern pattern) implements GuardedRegex {
        public GuardedRegexContains(String contains, String pattern) {
            this(contains, Pattern.compile(pattern));
        }

        public boolean test(String s) {
            return s.contains(contains) && pattern.matcher(s).find();
        }
    }
    private record GuardedRegexStartsWith(String start,  Pattern pattern) implements GuardedRegex {
        public GuardedRegexStartsWith(String start, String pattern) {
            this(start, Pattern.compile(pattern));
        }

        public boolean test(String s) {
            return s.startsWith(start) && pattern.matcher(s).find();
        }
    }
    private record GuardedRegexEndsWith(String end, Pattern pattern) implements GuardedRegex {
        public GuardedRegexEndsWith(String end, String pattern) {
            this(end, Pattern.compile(pattern));
        }

        public boolean test(String s) {
            return s.endsWith(end) && pattern.matcher(s).find();
        }
    }
}
