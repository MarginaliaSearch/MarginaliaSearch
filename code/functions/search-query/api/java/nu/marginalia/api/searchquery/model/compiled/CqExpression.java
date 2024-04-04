package nu.marginalia.api.searchquery.model.compiled;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

/** Expression in a parsed index service query
 *
 */
public sealed interface CqExpression {

    Stream<Word> stream();

    /** @see nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates */
    long visit(LongVisitor visitor);
    /** @see nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates */
    double visit(DoubleVisitor visitor);
    /** @see nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates */
    int visit(IntVisitor visitor);
    /** @see nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates */
    boolean visit(BoolVisitor visitor);

    <T> T visit(ObjectVisitor<T> visitor);

    static CqExpression empty() {
        return new Or(List.of());
    }


    record And(List<? extends CqExpression> parts) implements CqExpression {
        @Override
        public Stream<Word> stream() {
            return parts.stream().flatMap(CqExpression::stream);
        }

        @Override
        public long visit(LongVisitor visitor) {
            return visitor.onAnd(parts);
        }

        @Override
        public double visit(DoubleVisitor visitor) {
            return visitor.onAnd(parts);
        }

        @Override
        public int visit(IntVisitor visitor) {
            return visitor.onAnd(parts);
        }

        @Override
        public boolean visit(BoolVisitor visitor) {
            return visitor.onAnd(parts);
        }

        @Override
        public <T> T visit(ObjectVisitor<T> visitor) { return visitor.onAnd(parts); }

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
        public long visit(LongVisitor visitor) {
            return visitor.onOr(parts);
        }

        @Override
        public double visit(DoubleVisitor visitor) {
            return visitor.onOr(parts);
        }

        @Override
        public int visit(IntVisitor visitor) {
            return visitor.onOr(parts);
        }

        @Override
        public boolean visit(BoolVisitor visitor) {
            return visitor.onOr(parts);
        }

        @Override
        public <T> T visit(ObjectVisitor<T> visitor) { return visitor.onOr(parts); }

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
        public long visit(LongVisitor visitor) {
            return visitor.onLeaf(idx);
        }

        @Override
        public double visit(DoubleVisitor visitor) {
            return visitor.onLeaf(idx);
        }

        @Override
        public int visit(IntVisitor visitor) {
            return visitor.onLeaf(idx);
        }

        @Override
        public boolean visit(BoolVisitor visitor) {
            return visitor.onLeaf(idx);
        }

        @Override
        public <T> T visit(ObjectVisitor<T> visitor) { return visitor.onLeaf(idx); }

        @Override
        public String toString() {
            return Integer.toString(idx);
        }
    }

    interface LongVisitor {
        long onAnd(List<? extends CqExpression> parts);
        long onOr(List<? extends CqExpression> parts);
        long onLeaf(int idx);
    }

    interface IntVisitor {
        int onAnd(List<? extends CqExpression> parts);
        int onOr(List<? extends CqExpression> parts);
        int onLeaf(int idx);
    }

    interface BoolVisitor {
        boolean onAnd(List<? extends CqExpression> parts);
        boolean onOr(List<? extends CqExpression> parts);
        boolean onLeaf(int idx);
    }

    interface DoubleVisitor {
        double onAnd(List<? extends CqExpression> parts);
        double onOr(List<? extends CqExpression> parts);
        double onLeaf(int idx);
    }

    interface ObjectVisitor<T> {
        T onAnd(List<? extends CqExpression> parts);
        T onOr(List<? extends CqExpression> parts);
        T onLeaf(int idx);
    }

}
