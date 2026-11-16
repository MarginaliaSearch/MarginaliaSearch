package nu.marginalia.index.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Scratch variant of IntArrayList for borrowers that overwrite the full
 *  range wholesale, e.g. via a bulk copy into the backing array.
 *  <p></p>
 *  We disable zero fill for resizing because it's imperative we go fast.
 *  */
public class ScratchIntList extends IntArrayList {

    public ScratchIntList(int capacity) {
        super(capacity);
    }

    public void setSizeForOverwrite(int size) {
        ensureCapacity(size);
        this.size = size;
    }
}
