package nu.marginalia.index;

import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;

public class ReverseIndexParameters
{
    public static final BTreeContext prioDocsBTreeContext = new BTreeContext(5, 1, BTreeBlockSize.BS_2048);
    public static final BTreeContext fullDocsBTreeContext = new BTreeContext(5, 2, BTreeBlockSize.BS_2048);
    public static final BTreeContext wordsBTreeContext = new BTreeContext(5, 2, BTreeBlockSize.BS_2048);
}
