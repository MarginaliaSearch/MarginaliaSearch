package nu.marginalia.util.transform_list;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Recursive descent-style parser utility.
 * <p>
 * Allows readable and easy mutation of a list.
 * </p>
 * Examples:
 * <code>
 * <pre>
 *     tl.transformEach(entity -> {
 *         if (foo(entity.value))
 *           entity.remove();
 *         if (bar(entity.value))
 *           entity.replace(Bar(10));
 *     }
 *
 *     tl.transformEachPair((a,b) -> {
 *          if ("-".equals(a.value.str) && Number.equals(b.value.type)) {
 *              a.remove();
 *              b.replace(new Number(-b.value.val));
 *          }
 *     }
 *
 *     list.scanAndTransform(TokenType.LPAREN, TokenType.RPAREN, itemsBetween -> { });
 * </pre>
 * </code>
 */
public class TransformList<T> {
    private final List<T> backingList;

    public TransformList(List<T> backingList) {
        this.backingList = backingList;
    }

    public void transformEach(Consumer<Entity> consumer) {
        for (var iter = backingList.listIterator(); iter.hasNext(); ) {
            var entity = new Entity(iter.next());
            consumer.accept(entity);
            if (entity.action == Action.REPLACE) {
                iter.set(entity.value);
            }
            else if (entity.action == Action.REMOVE) {
                iter.remove();
            }
        }
    }

    public void transformEachPair(BiConsumer<Entity, Entity> consumer) {
        for (var iter = backingList.listIterator(); iter.hasNext(); ) {
            var firstEntity = new Entity(iter.next());
            if (!iter.hasNext()) break;
            var secondEntry = new Entity(backingList.get(iter.nextIndex()));

            consumer.accept(firstEntity, secondEntry);
            if (firstEntity.action == Action.REPLACE) {
                iter.set(firstEntity.value);

                if (secondEntry.action == Action.REPLACE) {
                    backingList.set(iter.nextIndex(), secondEntry.value);
                }
                else if (secondEntry.action == Action.REMOVE) {
                    iter.next();
                    iter.remove();
                }
            }
            else if (firstEntity.action == Action.REMOVE) {
                if (secondEntry.action == Action.REPLACE) {
                    backingList.set(iter.nextIndex(), secondEntry.value);
                }

                iter.remove();

                if (secondEntry.action == Action.REMOVE) {
                    iter.next();
                    iter.remove();
                }
            }

        }
    }

    public void scan(Predicate<T> start, Predicate<T> end, Consumer<TransformList<T>> inbetween) {
        for (int i = 0; i < backingList.size(); i++) {
            if (start.test(backingList.get(i))) {
                for (int j = i + 1; j < backingList.size(); j++) {
                    if (end.test(backingList.get(j))) {
                        inbetween.accept(new TransformList<>(backingList.subList(i, j+1)));
                        break;
                    }
                }
            }
        }
    }

    public void scanAndTransform(Predicate<T> start, Predicate<T> end, Consumer<Entity> inbetweenConsumer) {
        scan(start, end, range -> range.transformEach(inbetweenConsumer));
    }

    public int size() {
        return backingList.size();
    }

    public List<T> getBackingList() {
        return backingList;
    }


    /** Represents a mutable item in the transform list */
    public class Entity {
        private T value;
        private Action action;

        Entity(T value) {
            this.value = value;
            this.action = Action.NO_OP;
        }

        public T value() {
            return value;
        }

        public void replace(T newValue) {
            action = Action.REPLACE;
            value = newValue;
        }

        public void remove() {
            action = Action.REMOVE;
        }
    }

    enum Action {
        NO_OP,
        REPLACE,
        REMOVE
    }
}
