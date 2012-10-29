package net.csdn.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.mongo.association.*;
import net.csdn.mongo.commands.Delete;
import net.csdn.mongo.commands.Save;
import net.csdn.mongo.embedded.AssociationEmbedded;
import net.csdn.mongo.embedded.BelongsToAssociationEmbedded;
import net.csdn.mongo.embedded.HasManyAssociationEmbedded;
import net.csdn.mongo.embedded.HasOneAssociationEmbedded;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-16
 * Time: 下午8:11
 * # The +Document+ class is the Parent Class of All MongoDB model,
 * # this means any MongoDB model class should extends this class.
 * #
 * # Once your class extends Document,you get all ODM(Object Data Mapping )
 * # and +net.csdn.mongo.Criteria+ (A convenient DSL) power.
 * #
 * # Example setup:
 * #
 * # ```java
 * # public class Person extends Document{
 * #    static{
 * #         storeIn("persons")
 * #     }
 * # }
 * #```
 * #
 */
public class Document {

    //instance attributes
    protected DBObject attributes = new BasicDBObject();
    protected boolean new_record = true;
    protected Map<String, Association> associations = map();
    protected Map<String, AssociationEmbedded> associationsEmbedded = map();


    //for embedded association
    public Document _parent;
    public String associationEmbeddedName;


    /*
    *  +Class Methods+ will be copied into subclass when system startup.
    *  you should access them using class methods instead of directly accessing;
    *
    *  Example:
    *
    *  ```java
    *  Person.collection();
    *  ```
    *  instead of
    *
    *  ```java
    *     Person.parent$_collection;//this is a wrong way to access class attributes
    *  ```
    *
    */
    protected static Map parent$_primaryKey;
    protected static boolean parent$_embedded;

    protected static List<String> parent$_fields;

    protected static DBCollection parent$_collection;
    protected static String parent$_collectionName;
    protected static Map<String, Association> parent$_associations;
    protected static Map<String, AssociationEmbedded> parent$_associations_embedded;

    public static MongoMongo mongoMongo;


    /*
     * Warning: all methods  modified by +static+ keyword , you should call them in subclass.
     */
    public static String collectionName() {
        return parent$_collectionName;
    }

    public static boolean embedded() {
        return parent$_embedded;
    }

    public static boolean embedded(boolean isEmbedded) {
        parent$_embedded = isEmbedded;
        return parent$_embedded;
    }


    public static DBCollection collection() {
        return parent$_collection;
    }

    public static Map<String, Association> associationsMetaData() {
        return parent$_associations;
    }

    public static Map<String, AssociationEmbedded> associationsEmbeddedMetaData() {
        return parent$_associations_embedded;
    }

    public static List fields() {
        return parent$_fields;
    }


    /*
     # setting the collection name to store in.
     #
     # Example:
     #
     # <tt>Person.store_in :populdation</tt>
     #
     # Warning: you should call this Class Method in subclass.
    */
    protected static DBCollection storeIn(String name) {

        parent$_collectionName = name;
        parent$_collection = mongoMongo.database().getCollection(name);
        return parent$_collection;
    }

    public static <T extends Document> T create(Map map) {
        throw new AutoGeneration();
    }

    public static <T extends Document> T create(DBObject object) {
        throw new AutoGeneration();
    }


    /*
    def generate_key
       if primary_key
         values = primary_key.collect { |key| @attributes[key] }
         @attributes[:_id] = values.join(" ").parameterize.to_s
       else
         @attributes[:_id] = Mongo::ObjectID.new.to_s unless id
       end
    end
    */
    public void key(String... names) {

    }

    public void save() {
        Save.execute(this, false);
    }

    public void remove() {
        Delete.execute(this);
    }

    /*
      # Remove a child document from this parent +Document+. Will reset the
      # memoized association and notify the parent of the change.
      def remove(child)
        name = child.association_name
        reset(name) { @attributes.remove(name, child.attributes) }
        notify
      end
     */
    public void remove(Document child) {
        String name = child.associationEmbeddedName;
        AssociationEmbedded association = this.associationEmbedded().get(name);
        if (association instanceof HasManyAssociationEmbedded) {
            List<Map> children = (List) this.attributes().get(name);
            Map shouldRemove = null;
            for (Map wow : children) {
                if (child.id().equals(wow.get("_id"))) {
                    shouldRemove = wow;
                    break;
                }
            }
            if (shouldRemove != null) {
                children.remove(shouldRemove);
            }

        } else {
            this.attributes().removeField(name);
        }

        this.associationEmbedded().get(name).remove(child);
        this.save();
    }


    /* +copySingleAttributeToPojoField+ and  +copyAllAttributesToPojoFields+
      since all model have attributes property,so we should sync values between
      Pojo fields and  attributes property
    */
    protected void copySingleAttributeToPojoField(String setterMethodName, Object param) {
        ReflectHelper.method(this, setterMethodName, param);
    }

    protected void copyAllAttributesToPojoFields() {
        Field[] fields = this.getClass().getDeclaredFields();
        List allFields = list();
        for (Field field : fields) {
            allFields.add(field.getName());
        }

        Set keys = attributes.keySet();
        for (Object key : keys) {
            if (key instanceof String) {
                String strKey = (String) key;
                try {
                    if (allFields.contains(strKey)) {
                        ReflectHelper.field(this, strKey, attributes.get(strKey));
                    }

                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
        }

    }

    public Object id() {
        return attributes.get("_id");
    }

    public Object id(Object id) {
        attributes.put("_id", id);
        return id;
    }

    public Document fields(String... names) {

        for (String name : names) {
            fields().add(name);
        }
        return this;
    }

    public Map<String, Association> associations() {
        return associations;
    }

    public Map<String, AssociationEmbedded> associationEmbedded() {
        return associationsEmbedded;
    }

    public DBObject reload() {
        attributes = collection().findOne(map("_id", attributes.get("_id")));
        return attributes;
    }

    public DBObject attributes() {
        return attributes;
    }


    //embedded Association methods

    public static HasManyAssociationEmbedded hasManyEmbedded(String name, Options options) {
        HasManyAssociationEmbedded association = new HasManyAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;
    }

    public static BelongsToAssociationEmbedded belongsToEmbedded(String name, Options options) {
        BelongsToAssociationEmbedded association = new BelongsToAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;

    }

    public static HasOneAssociationEmbedded hasOneEmbedded(String name, Options options) {
        HasOneAssociationEmbedded association = new HasOneAssociationEmbedded(name, options);
        if (parent$_associations_embedded == null) parent$_associations_embedded = map();
        parent$_associations_embedded.put(name, association);
        return association;
    }

    //Association methods
    public static HasManyAssociation hasMany(String name, Options options) {
        HasManyAssociation association = new HasManyAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;
    }

    public static HasOneAssociation hasOne(String name, Options options) {
        HasOneAssociation association = new HasOneAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;

    }

    public static BelongsToAssociation belongsTo(String name, Options options) {
        BelongsToAssociation association = new BelongsToAssociation(name, options);
        if (parent$_associations == null) parent$_associations = map();
        parent$_associations.put(name, association);
        return association;
    }


    //bind Criteria

    public static Criteria where(Map conditions) {
        //return new Criteria(Document.class).where(conditions);
        throw new AutoGeneration();
    }

    public static Criteria select(List fieldNames) {
        throw new AutoGeneration();
    }

    public static Criteria order(List orderBy) {
        throw new AutoGeneration();
    }

    public static Criteria skip(int skip) {
        throw new AutoGeneration();
    }

    public static Criteria limit(int limit) {
        throw new AutoGeneration();
    }

    public static int count() {
        throw new AutoGeneration();
    }

    public static Criteria in(Map in) {
        throw new AutoGeneration();
    }

    public static Criteria not(Map not) {
        throw new AutoGeneration();
    }

    public static Criteria notIn(Map notIn) {
        throw new AutoGeneration();
    }

    public static <T extends Document> T findById(Object id) {
        throw new AutoGeneration();
    }

    public static <T extends Document> List<T> find(List list) {
        throw new AutoGeneration();
    }

}
