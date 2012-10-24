package net.csdn.mongo.embedded;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-10-24
 * Time: 下午2:12
 */
public class BelongsToAssociationEmbedded implements AssociationEmbedded {

    private Class kclass;
    private Document document;
    private String name;

    private Document master;


    public BelongsToAssociationEmbedded(String name, Options options) {
        this.kclass = options.kClass();
        this.name = name;
    }

    private BelongsToAssociationEmbedded(String name, Class kclass, Document document) {
        this.name = name;
        this.kclass = kclass;
        this.document = document;
        master = document._parent;
    }

    @Override
    public AssociationEmbedded build(Map params) {
        return this;
    }

    @Override
    public AssociationEmbedded remove(Document document) {
        return this;
    }


    @Override
    public AssociationEmbedded doNotUseMePlease_newMe(Document document) {
        BelongsToAssociationEmbedded belongsToAssociationEmbedded = new BelongsToAssociationEmbedded(name, kclass, document);
        document.associationEmbedded().put(name, belongsToAssociationEmbedded);
        return belongsToAssociationEmbedded;
    }

    @Override
    public void save() {
        document.save();
    }

    @Override
    public List find(Object... ids) {
        return list(master);
    }

    @Override
    public <T extends Document> T findOne() {
        return (T) master;
    }


    @Override
    public Criteria filter() {
        return null;
    }
}
