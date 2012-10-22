package net.csdn.mongo.association;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;
import net.csdn.reflect.ReflectHelper;

import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class HasOneAssociation implements Association {

    private String foreignKey;
    private Document document;
    private Document parentDocument;
    private Class kclass;
    private String name;

    public HasOneAssociation(String name, Options options) {
        kclass = options.kClass();
        foreignKey = options.foreignKey();
    }

    private HasOneAssociation(Class kclass, String foreignKey, Document document) {
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
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Association doNotUseMePlease_newMe(Document document) {
        HasOneAssociation association = new HasOneAssociation(kclass, foreignKey, document);
        document.associations().put(name, association);
        return association;
    }

    @Override
    public void save() {
        document.save();
        document.reload();
        parentDocument.attributes().put(foreignKey, document.attributes().get("_id"));
        parentDocument.save();
    }

    @Override
    public Criteria filter() {
        return new Criteria(kclass).where(map("_id", document.attributes().get(foreignKey)));
    }
}
