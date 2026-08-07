package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Scratch variant of IntArrayList for borrowers that overwrite the full
 *  range wholesale, e.g. via a bulk copy into the backing array. */
public class ScratchIntList extends IntArrayList {

    public ScratchIntList(int capacity) {
        super(capacity);
    }

    /** Set the logical size without zero filling the extension, unlike
     *  size(int).  The contents between the old and new size are undefined
     *  until the caller writes them. */
    public void setSizeForOverwrite(int size) {
        ensureCapacity(size);
        this.size = size;
    }
}
