package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;
import java.util.stream.Stream;

/** Expression in a parsed index service query
 *
 */
public sealed interface CqExpression {

    Stream<Word> stream();
    List<IntList> paths();

    static CqExpression empty() {
        return new Or(List.of());
    }

    static List<IntList> allPaths(CqExpression expression) {

        var pathsRaw = expression.paths();

        if (pathsRaw.isEmpty())
            return pathsRaw;

        Set<IntList> ret = new LinkedHashSet<>(pathsRaw.size());

        for (IntList list: pathsRaw) {
            // Remove pathological cases potentially introduced by empty branches
            if (list.isEmpty()) continue;

            // sort, unique, and make immutable each the paths list
            ret.add(new IntImmutableList(new IntAVLTreeSet(list)));
        }

        return new ArrayList<>(ret);
    }

    record And(List<? extends CqExpression> parts) implements CqExpression {
        @Override
        public Stream<Word> stream() {
            return parts.stream().flatMap(CqExpression::stream);
        }

        @Override
        public List<IntList> paths() {

            if (parts.isEmpty()) return List.of();
            if (parts.size() == 1) return parts.getFirst().paths();

            List<IntList> ret = new ArrayList<>();

            ret.addAll(parts.getFirst().paths());

            for (int i = 1; i < parts.size(); i++) {
                var toCombine = parts.get(i).paths();
                List<IntList> newRet = new ArrayList<>(ret.size() * toCombine.size());

                for (int a = 0; a < ret.size(); a++) {
                    IntList aList = ret.get(a);
                    for (int b = 0; b < toCombine.size(); b++) {
                        IntList bList = toCombine.get(b);

                        IntList combinedList = new IntArrayList(aList.size() * bList.size());

                        combinedList.addAll(aList);
                        combinedList.addAll(bList);

                        newRet.add(combinedList);
                    }
                }
                ret = newRet;
            }
            return ret;
        }

        public String toString() {
            StringJoiner sj = new StringJoiner(", ", "And[ ", "]");
            parts.forEach(part -> sj.add(part.toString()));
            return sj.toString();
        }

    }

    record Or(List<? extends CqExpression> parts) implements CqExpression {
        @Override
        public Stream<Word> stream() {
            return parts.stream().flatMap(CqExpression::stream);
        }

        @Override
        public List<IntList> paths() {
            List<IntList> ret = new ArrayList<>(parts.size());
            for (var part : parts) {
                ret.addAll(part.paths());
            }
            return ret;
        }

        public String toString() {
            StringJoiner sj = new StringJoiner(", ", "Or[ ", "]");
            parts.forEach(part -> sj.add(part.toString()));
            return sj.toString();
        }


    }

    record Word(int idx) implements CqExpression {
        @Override
        public Stream<Word> stream() {
            return Stream.of(this);
        }

        @Override
        public List<IntList> paths() {
            return List.of(IntList.of(idx));
        }

        @Override
        public String toString() {
            return Integer.toString(idx);
        }
    }

    record Ignore() implements CqExpression {
        public static final Ignore INSTANCE = new Ignore();

        @Override
        public Stream<Word> stream() {
            return Stream.empty();
        }

        @Override
        public List<IntList> paths() {
            return List.of(IntList.of());
        }

        @Override
        public String toString() {
            return "ignore";
        }
    }

}
