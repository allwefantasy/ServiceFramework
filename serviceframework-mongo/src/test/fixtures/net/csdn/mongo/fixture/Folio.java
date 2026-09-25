package net.csdn.mongo.fixture;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.mongo.association.Options;
import net.csdn.mongo.embedded.AssociationEmbedded;

import static net.csdn.common.collections.WowCollections.map;

public class Folio extends Volume {
    static {
        storeIn(Names.of("folio"));
        hasManyEmbedded("plates", new Options(map(
                Options.n_kclass, Page.class
        )));
    }

    public AssociationEmbedded plates() {
        throw new AutoGeneration();
    }

    private String mark;

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }
}
