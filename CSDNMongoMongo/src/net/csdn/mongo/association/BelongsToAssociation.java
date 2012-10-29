package net.csdn.mongo.association;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class BelongsToAssociation implements Association {

    private String foreignKey;
    private Document document;
    private Document parentDocument;
    private Class kclass;
    private String name;


    public BelongsToAssociation(String name, Options options) {
        kclass = options.kClass();
        foreignKey = options.foreignKey();
        this.name = name;

    }

    private BelongsToAssociation(Class kclass, String foreignKey, Document document) {
        this.kclass = kclass;
        this.foreignKey = foreignKey;
        this.document = document;

    }

    @Override
    public Association build(Map params) {
        parentDocument = (Document) ReflectHelper.staticMethod(kclass, "create", params);
        return this;
    }

    @Override
    public Association remove(Document document) {
        return this;
    }

    @Override
    public Association doNotUseMePlease_newMe(Document document) {
        BelongsToAssociation belongsToAssociation = new BelongsToAssociation(kclass, foreignKey, document);
        document.associations().put(name, belongsToAssociation);
        return belongsToAssociation;
    }

    @Override
    public void save() {
        parentDocument.save();
        parentDocument.reload();
        document.attributes().put(foreignKey, parentDocument.attributes().get("_id"));
    }

    @Override
    public Criteria filter() {
        return new Criteria(kclass).where(map("_id", document.attributes().get(foreignKey)));
    }
}
