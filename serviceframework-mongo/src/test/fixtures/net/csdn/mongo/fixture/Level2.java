package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

public class Level2 extends Level1 {
    static {
        storeIn(Names.of("l2"));
        hasMany("notes", new Options(map(
                Options.n_kclass, Note.class,
                Options.n_foreignKey, "level2_id"
        )));
    }

    public Association notes() {
        throw new AutoGeneration();
    }

    private String mid;

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }
}
