package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Association;
import net.csdn.mongo.association.Options;

import static net.csdn.common.collections.WowCollections.map;

public class Item extends Document {
    static {
        storeIn(Names.of("item"));
        belongsTo("owner", new Options(map(
                Options.n_kclass, Owner.class,
                Options.n_foreignKey, "owner_id"
        )));
    }

    public Association owner() {
        throw new AutoGeneration();
    }

    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
