package net.csdn.mongo.conflict;

import net.csdn.mongo.fixture.Names;

public class LockedChild extends LockedParent {
    static {
        storeIn(Names.of("locked_child"));
    }
}
