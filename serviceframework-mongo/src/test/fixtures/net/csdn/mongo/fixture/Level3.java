package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

public class Level3 extends Level2 {
    static {
        storeIn(Names.of("l3"));
        alias("name", "l3");
        hasMany("notes", new Options(map(
                Options.n_kclass, Note.class,
                Options.n_foreignKey, "leaf_note_id"
        )));
        hasMany("extras", new Options(map(
                Options.n_kclass, Note.class,
                Options.n_foreignKey, "level3_id"
        )));
    }

    public Association extras() {
        throw new AutoGeneration();
    }

    private String leaf;

    public String getLeaf() {
        return leaf;
    }

    public void setLeaf(String leaf) {
        this.leaf = leaf;
    }
}
