package net.csdn.mongo.association;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;

import java.util.List;
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



    public Criteria where(Map conditions);

    public Criteria select(List fieldNames);

    public Criteria order(Map orderBy);

    public Criteria skip(int skip);

    public Criteria limit(int limit);

    public int count();

    public Criteria in(Map in);

    public Criteria not(Map not);

    public Criteria notIn(Map notIn);

    public <T> T findById(Object id);
    public <T> T findOne();

    public <T> List<T> find(List list);

    public <T> List<T> findAll();

}
