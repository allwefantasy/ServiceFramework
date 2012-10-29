package net.csdn.mongo.association;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 上午10:09
 */
public class HasManyAssociation implements Association {

    private Class kclass;
    private String foreignKey;
    private Document document;
    private String name;

    private List<Document> documentList = list();


    public HasManyAssociation(String name, Options options) {
        kclass = options.kClass();
        foreignKey = options.foreignKey();
        this.name = name;

    }

    private HasManyAssociation(Class kclass, String foreignKey, Document document) {
        this.kclass = kclass;
        this.foreignKey = foreignKey;
        this.document = document;

    }


    @Override
    public Association build(Map params) {
        Document child = (Document) ReflectHelper.staticMethod(kclass, "create", params);
        documentList.add(child);
        return this;
    }

    @Override
    public Association remove(Document document) {
        documentList.remove(document);
        document.remove();
        return this;
    }

    @Override
    public void save() {
        document.save();
        for (Document subDoc : documentList) {
            subDoc.attributes().put(foreignKey, document.attributes().get("_id"));
            subDoc.save();
            Map<String, Association> associationMap = subDoc.associations();
            //cascade save
            for (Map.Entry<String, Association> entry : associationMap.entrySet()) {
                if (entry.getValue() instanceof HasManyAssociation || entry.getValue() instanceof HasOneAssociation) {
                    entry.getValue().save();
                }
            }
        }
    }

    @Override
    public Criteria filter() {
        return new Criteria(kclass).where(map(foreignKey, document.attributes().get("_id")));
    }


    @Override
    public Association doNotUseMePlease_newMe(Document document) {
        HasManyAssociation instanceHasManyAssociation = new HasManyAssociation(this.kclass, this.foreignKey, document);
        document.associations().put(name, instanceHasManyAssociation);
        return instanceHasManyAssociation;
    }
}
