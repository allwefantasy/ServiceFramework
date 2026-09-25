package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;
import net.csdn.mongo.embedded.AssociationEmbedded;

import static net.csdn.common.collections.WowCollections.map;

public class Volume extends Document {
    static {
        storeIn(Names.of("volume"));
        hasManyEmbedded("pages", new Options(map(
                Options.n_kclass, Page.class
        )));
    }

    public AssociationEmbedded pages() {
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
