package net.csdn.mongo.association;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.isEmpty;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class HasOneAssociation implements Association {

    private String foreignKey;
    private Document document;
    private Document childDocument;
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
        childDocument = (Document) ReflectHelper.staticMethod(kclass, "create", params);
        return this;
    }

    @Override
    public Association remove(Document document) {
        childDocument = null;
        document.remove();
        return this;
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
        childDocument.attributes().put(foreignKey, document.attributes().get("_id"));
        childDocument.save();
        Map<String, Association> associationMap = childDocument.associations();
        //cascade save
        for (Map.Entry<String, Association> entry : associationMap.entrySet()) {
            if (entry.getValue() instanceof HasManyAssociation || entry.getValue() instanceof HasOneAssociation) {
                entry.getValue().save();
            }
        }
    }
    public Criteria where(Map conditions) {
        return filter().where(conditions);
    }

    public Criteria select(List fieldNames) {
        return filter().select(fieldNames);
    }

    public Criteria order(Map orderBy) {
        return filter().order(orderBy);
    }

    public Criteria skip(int skip) {
        return filter().skip(skip);
    }

    public Criteria limit(int limit) {
        return filter().limit(limit);
    }

    public int count() {
        return filter().count();
    }

    public Criteria in(Map in) {
        return filter().in(in);
    }

    public Criteria not(Map not) {
        return filter().not(not);
    }

    public Criteria notIn(Map notIn) {
        return filter().notIn(notIn);
    }

    public <T> T findById(Object id) {
        return filter().findById(id);
    }

    @Override
    public <T> T findOne() {
        List<T> items = findAll();
        if (isEmpty(items)) return null;
        return items.get(0);
    }

    public <T> List<T> find(List list) {
        return filter().find(list);
    }

    public <T> List<T> findAll() {
        return filter().findAll();
    }


    private Criteria filter() {
        return new Criteria(kclass).where(map("_id", document.attributes().get(foreignKey)));
    }
}
