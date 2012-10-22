package net.csdn.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import net.csdn.reflect.ReflectHelper;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午4:57
 * <p/>
 * # The +Criteria+ class is the core object needed in MongoSupport to retrieve
 * # objects from the database. It is a DSL that essentially sets up the
 * # selector and options arguments that get passed on to a <tt>Mongo Collection</tt>
 * # in the Java driver. Each method on the +Criteria+ returns self to they
 * # can be chained in order to create a readable criterion to be executed
 * # against the database.
 * #
 * # Example setup:
 * #
 * # ```java
 * # Criteria criteria = new Criteria()
 * # ```
 * #
 * # ```java
 * # criteria.select("filed").where(map("field","value")).skip(20).limit(20)
 * # ```
 * #
 * # ```java
 * # <tt>criteria.execute</tt>
 * #```
 */
public class Criteria {
    // attr_reader :klass, :options, :selector
    private Class<Document> kclass;
    private Map options = map();
    private Map selector = map();
    private int count = 0;

    private final static String AGGREGATE_REDUCE = "function(obj, prev) { prev.count++; }";

    /*
    # Aggregate the criteria. This will take the internally built selector and options
    # and pass them on to the Ruby driver's +group()+ method on the collection. The
    # collection itself will be retrieved from the class provided, and once the
    # query has returned it will provided a grouping of keys with counts.
    #
    # Example:
    #
    # <tt>criteria.select(:field1).where(:field1 => "Title").aggregate(Person)</tt>
    def aggregate
      @klass.collection.group(@options[:$fields], @selector, { :count => 0 }, AGGREGATE_REDUCE, true)
    end
     */
    public Criteria aggregate() {
        ReflectHelper.method("", "");
        return this;
    }


    public Criteria not(Map attributes) {
        updateSelector(attributes, "$ne");
        return this;
    }

    /*
    def where(selector = nil)
      case selector
      when String
        @selector.update("$where" => selector)
      else
        @selector.update(selector ? selector.expand_complex_criteria : {})
      end
      self
    end
     */
    public Criteria where(Map _selector) {
        selector.putAll(_selector);
        return this;
    }

    public Criteria where(String _selector) {
        selector.put("$where", _selector);
        return this;
    }

    /*
    # Adds a criterion to the +Criteria+ that specifies values that must all
    # be matched in order to return results. Similar to an "in" clause but the
    # underlying conditional logic is an "AND" and not an "OR". The MongoDB
    # conditional operator that will be used is "$all".
    #
    # Options:
    #
    # attributes: A +Hash+ where the key is the field name and the value is an
    # +Array+ of values that must all match.
    #
    # Example:
    #
    # <tt>criteria.all(map("field",list("value1","value2")))</tt>
    #
    # <tt>criteria.all(map("field",list("value1","value2"),"field1",list("value3")))</tt>
    #
    # Returns: <tt>self</tt>
     */
    public Criteria all(Map _selector) {
        updateSelector(_selector, "$all");
        return this;
    }

    public List<Document> find(List list) {
        return in(map("_id", list)).fetch();
    }

    public Document findById(Object id) {
        return where(map("_id", id)).singleFetch();
    }

    /*
    # Adds a criterion to the +Criteria+ that specifies values that must
    # be matched in order to return results. This is similar to a SQL "WHERE"
    # clause. This is the actual selector that will be provided to MongoDB,
    # similar to the Javascript object that is used when performing a find()
    # in the MongoDB console.
    #
    # Options:
    #
    # _selectior: A +Hash+ that must match the attributes of the +Document+.
    #
    # Example:
    #
    # <tt>criteria.and(map("filed","value","field1","value1"))</tt>
    #
    # Returns: <tt>self</tt>
     */
    public Criteria and(Map _selector) {
        return where(_selector);
    }


    public int count() {
        return collection().find(translateMapToDBObject(selector)).count();
    }

    public Criteria in(Map _selector) {
        updateSelector(_selector, "$in");
        return this;
    }


    public Criteria id(Object id) {
        selector.put("_id", id);
        return this;
    }

    /*
    # Adds a criterion to the +Criteria+ that specifies values where none
    # should match in order to return results. This is similar to an SQL "NOT IN"
    # clause. The MongoDB conditional operator that will be used is "$nin".
    #
    # Options:
    #
    # exclusions: A +Hash+ where the key is the field name and the value is an
    # +Array+ of values that none can match.
    #
    # Example:
    #
    # <tt>criteria.notIn(map("field",list("value","value1")))</tt>
    #
    # <tt>criteria.notIn(map("field",list("value","value1"),"filed1",list("value2","value3")))</tt>
    #
    # Returns: <tt>self</tt>
     */
    public Criteria notIn(Map _selector) {
        updateSelector(_selector, "$nin");
        return this;
    }


    /*
    # Adds a criterion to the +Criteria+ that finally you  fetch data from mongodb
    #
    #
    # Example:
    #
    # <tt>Person person = criteria.singleFetch()</tt>
    #
    #
    # Returns: <tt>subclass of document</tt>
     */
    public Document singleFetch() {
        DBObject dbObject = collection().findOne(translateMapToDBObject(selector));
        Document document = (Document) ReflectHelper.staticMethod(kclass, "create", dbObject.toMap());
        return document;
    }


    public List fetch() {

        List result = list();
        DBCursor dbCursor = collection().find(translateMapToDBObject(selector), translateMapToDBObject(options));
        while (dbCursor.hasNext()) {
            DBObject dbObject = dbCursor.next();
            result.add(ReflectHelper.staticMethod(kclass, "create", dbObject.toMap()));
        }
        return result;
    }


    public Criteria select(String... fieldNames) {
        options.put("fields", fieldNames);
        return this;
    }

    public Criteria order(List orderBy) {
        options.put("sort", orderBy);
        return this;
    }

    public Criteria skip(int skip) {
        options.put("skip", skip);
        return this;
    }

    public Criteria limit(int limit) {
        options.put("limit", limit);
        return this;
    }

    private Criteria updateSelector(Map attributes, String operator) {
        for (Object key : attributes.keySet()) {
            Object value = attributes.get(key);
            selector.put(key, map(operator, value));
        }
        return this;
    }

    private DBObject translateMapToDBObject(Map map) {
        DBObject query = new BasicDBObject();
        query.putAll(map);
        return query;
    }

    public DBCollection collection() {
        return (DBCollection) ReflectHelper.staticMethod(kclass, "collection");
    }

    public Criteria(Class<Document> kclass) {
        this.kclass = kclass;
    }


    private DBObject grouped(String start, String field, String reduce) {
        return collection().group(null, translateMapToDBObject(selector), new BasicDBObject("start", "start"), reduce.replaceAll("[field]", field));
    }


    private void process_options() {

    }

    /*
   # Filters the unused options out of the options +Hash+. Currently this
   # takes into account the "page" and "per_page" options that would be passed
   # in if using Paginator
    */

    private void filter_options() {
        Integer page_num = (Integer) options.remove("page");
        Integer per_page_num = (Integer) options.remove("per_page");
        if (page_num != null || per_page_num != null) {
            if (per_page_num == null) per_page_num = 20;
            if (page_num == null) page_num = 1;
            options.put("limit", per_page_num);
            options.put("skip", page_num * per_page_num - per_page_num);
        }
    }


}
