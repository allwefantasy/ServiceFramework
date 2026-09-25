package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

public class Owner extends Document {
    static {
        storeIn(Names.of("owner"));
        hasMany("items", new Options(map(
                Options.n_kclass, Item.class,
                Options.n_foreignKey, "owner_id"
        )));
    }

    public Association items() {
        throw new AutoGeneration();
    }

    private String label;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
