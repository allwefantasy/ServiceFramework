package net.csdn.mongo.embedded;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-10-23
 * Time: 下午7:38
 */
public class HasManyAssociationEmbedded implements AssociationEmbedded {
    private Class kclass;
    private Document document;
    private String name;

    private List<Document> children = list();


    public HasManyAssociationEmbedded(String name, Options options) {
        kclass = options.kClass();
        this.name = name;

    }


    private HasManyAssociationEmbedded(String name, Class kclass, Document document) {
        this.kclass = kclass;
        this.document = document;
        this.name = name;

        List<Map> attributes = (List<Map>) document.attributes().get(name);
        if (attributes == null) return;
        int i = 0;
        for (Map item : attributes) {
            Document child = (Document) ReflectHelper.staticMethod(kclass, "create", item);
            child._parent = document;
            if (child.id() == null) {
                child.attributes().put("_id", i);
                i++;
            }
            child.associationEmbeddedName = name;
            children.add(child);
        }
    }


    @Override
    public AssociationEmbedded build(Map params) {
        Document child = (Document) ReflectHelper.staticMethod(kclass, "create ", params);
        child._parent = document;
        child.associationEmbeddedName = name;
        child.attributes().put("_id", children.size() + 1);
        children.add(child);
        return this;
    }

    @Override
    public List find(Object... ids) {
        if (ids.length == 0) {
            return children;
        }
        List documents = list();
        for (Object id : ids) {
            for (Document doc : children) {
                if (id.equals(doc.id())) {
                    documents.add(doc);
                }
            }
        }
        return documents;
    }

    @Override
    public <T extends Document> T findOne() {
        List list = find();
        if (list.size() == 0) return null;
        return (T) (list.get(0));
    }


    @Override
    public AssociationEmbedded remove(Document document) {
        children.remove(document);
        return this;
    }

    @Override
    public AssociationEmbedded doNotUseMePlease_newMe(Document document) {
        HasManyAssociationEmbedded hasManyAssociationEmbedded = new HasManyAssociationEmbedded(name, kclass, document);
        document.associationEmbedded().put(name, hasManyAssociationEmbedded);
        return hasManyAssociationEmbedded;
    }


    @Override
    public void save() {
        document.save();
    }

    @Override
    public Criteria filter() {
        return null;
    }
}
