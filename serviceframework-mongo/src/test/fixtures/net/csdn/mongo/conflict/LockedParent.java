package net.csdn.mongo.conflict;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;
import net.csdn.mongo.fixture.Names;
import net.csdn.mongo.fixture.Note;

import static net.csdn.common.collections.WowCollections.map;

public class LockedParent extends Document {
    static {
        storeIn(Names.of("locked"));
        hasMany("notes", new Options(map(
                Options.n_kclass, Note.class,
                Options.n_foreignKey, "locked_id"
        )));
    }

    public final Association notes() {
        throw new AutoGeneration();
    }
}
