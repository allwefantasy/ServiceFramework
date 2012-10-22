package net.csdn.mongo.association;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-10-22
 * Time: 下午4:58
 */
public interface Association {

    public Association build(Map params);

    public Association remove(Document document);

    public Association doNotUseMePlease_newMe(Document document);

    public void save();

    public Criteria filter();

}
