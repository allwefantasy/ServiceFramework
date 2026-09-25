package net.csdn.mongo.miss;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.fixture.Names;

public class Ghost extends Document {
    static {
        storeIn(Names.of("ghost"));
    }

    public Association missing() {
        throw new AutoGeneration();
    }
}
