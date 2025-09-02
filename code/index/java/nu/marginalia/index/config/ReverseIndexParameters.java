package nu.marginalia.index.config;

import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;

public class ReverseIndexParameters
{
    public static final BTreeContext wordsBTreeContext = new BTreeContext(5, 2, BTreeBlockSize.BS_512);
}
