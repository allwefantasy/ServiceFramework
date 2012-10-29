package net.csdn.mongo.embedded;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-10-23
 * Time: 下午7:53
 */
public interface AssociationEmbedded {

    public AssociationEmbedded build(Map params);

    public AssociationEmbedded remove(Document document);

    public AssociationEmbedded doNotUseMePlease_newMe(Document document);

    public void save();

    public List find(Object... ids);

    public <T extends Document> T findOne();

    public Criteria filter();
}
