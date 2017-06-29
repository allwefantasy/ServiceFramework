package net.csdn.mongo.embedded;

import net.csdn.mongo.Document;
import net.csdn.mongo.association.Options;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.*;
import static net.csdn.common.reflect.ReflectHelper.staticMethod;

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
        //Children share the same HashMap in parent.So modify will be know by parent
        for (Map item : attributes) {
            Document child = (Document) staticMethod(kclass, "create9", item);
            child._parent = document;
            child.associationEmbeddedName = name;
            children.add(child);
        }
    }


    @Override
    public AssociationEmbedded build(Map params) {
        Map temp = map();
        temp.putAll(params);
        Document child = (Document) staticMethod(kclass, "create9", temp);
        child._parent = document;
        child.associationEmbeddedName = name;
        children.add(child);
        List childAttr = (List) document.attributes().get(name);
        if (isEmpty(childAttr)) document.attributes().put(name, list());
        childAttr = (List) document.attributes().get(name);
        childAttr.add(temp);
        return this;
    }


    @Override
    public List find() {
        return children;
    }


    //for now only support 'equal' match
    public List find(Map map) {
        List list = list();
        Map<String, Object> temp = map;
        for (Document doc : children) {
            boolean match = true;
            for (Map.Entry<String, Object> entry : temp.entrySet()) {
                if (!entry.getValue().equals(doc.attributes().get(entry.getKey()))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                list.add(doc);
            }
        }
        return list;
    }

    //return  the first element
    @Override
    public <T extends Document> T findOne() {
        List list = find();
        if (list.size() == 0) return null;
        return (T) (list.get(0));
    }

    @Override
    public Class kclass() {
        return kclass;
    }

    @Override
    public String name() {
        return name;
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


}
