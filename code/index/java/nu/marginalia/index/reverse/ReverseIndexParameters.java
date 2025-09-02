package nu.marginalia.index.reverse;

import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;

public class ReverseIndexParameters
{
    public static final BTreeContext wordsBTreeContext = new BTreeContext(5, 2, BTreeBlockSize.BS_512);
}
