package net.csdn.mongo.embedded;

import com.mongodb.DBObject;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-10-24
 * Time: 下午2:02
 */
public class HasOneAssociationEmbedded implements AssociationEmbedded {

    private Class kclass;
    private Document document;
    private String name;

    private Document child = null;

    public HasOneAssociationEmbedded(String name, Options options) {
        this.kclass = options.kClass();
        this.name = name;
    }

    private HasOneAssociationEmbedded(String name, Class kclass, Document document) {
        this.kclass = kclass;
        this.document = document;
        this.name = name;
        DBObject childMap = (DBObject) document.attributes().get(name);
        if (childMap != null) {
            child = (Document) ReflectHelper.staticMethod(kclass, "create", childMap);
            child._parent = document;
            child.associationEmbeddedName = name;
        }
    }


    @Override
    public AssociationEmbedded build(Map params) {
        child = (Document) ReflectHelper.staticMethod(kclass, "create", params);
        child._parent = document;
        child.associationEmbeddedName = name;
        return this;
    }

    @Override
    public AssociationEmbedded remove(Document document) {
        child = null;
        document.attributes().removeField(name);
        return this;
    }

    @Override
    public AssociationEmbedded doNotUseMePlease_newMe(Document document) {
        HasOneAssociationEmbedded hasOneAssociationEmbedded = new HasOneAssociationEmbedded(name, kclass, document);
        document.associationEmbedded().put(name, hasOneAssociationEmbedded);
        return hasOneAssociationEmbedded;
    }

    @Override
    public void save() {
        document.save();
    }

    @Override
    public List find(Object... ids) {
        return list(child);
    }

    @Override
    public <T extends Document> T findOne() {
        return (T) child;
    }


    @Override
    public Criteria filter() {
        return null;
    }
}
