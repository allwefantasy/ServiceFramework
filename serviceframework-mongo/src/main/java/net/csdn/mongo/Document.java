package net.csdn.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.exception.AutoGeneration;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.reflect.WowMethod;
import net.csdn.mongo.annotations.*;
import net.csdn.mongo.association.*;
import net.csdn.mongo.commands.Delete;
import net.csdn.mongo.commands.Insert;
import net.csdn.mongo.commands.Save;
import net.csdn.mongo.commands.Update;
import net.csdn.mongo.embedded.AssociationEmbedded;
import net.csdn.mongo.embedded.BelongsToAssociationEmbedded;
import net.csdn.mongo.embedded.HasManyAssociationEmbedded;
import net.csdn.mongo.embedded.HasOneAssociationEmbedded;
import net.csdn.mongo.validate.ValidateParse;
import net.csdn.mongo.validate.ValidateResult;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.*;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.common.reflect.ReflectHelper.staticField;
import static net.csdn.common.reflect.ReflectHelper.staticMethod;

/**
 * User: WilliamZhu
 * Date: 12-10-16
 * Time: 下午8:11
 * <p/>
 * The +Document+ class is the Parent Class of All MongoDB model,
 * this means any MongoDB model class should extends this class.
 * <p/>
 * Once your class extends Document,you get all ODM(Object Data Mapping )
 * and +net.csdn.mongo.Criteria+ (A convenient DSL) power.
 * <p/>
 * Example setup:
 * <p/>
 * ```java
 * public class Person extends Document{
 * static{
 * storeIn("persons")
 * }
 * }
 * ```
 */
public class Document {

    private static CSLogger logger = Loggers.getLogger(Document.class);

    //instance attributes
    @Transient
    protected Map attributes = map();
    @Transient
    protected boolean new_record = true;
    @Transient
    protected Map<String, Association> associations = map();
    @Transient
    protected Map<String, AssociationEmbedded> associationsEmbedded = map();


    //for embedded association
    @Transient
    public Document _parent;

    @Transient
    public String associationEmbeddedName;


    public <T> T attr(String key, Class<T> clzz) {
        return (T) attributes.get((String) staticMethod(this.getClass(), "translateFromAlias", key));
    }

    public Document attr(String key, Object obj) {
        attributes.put((String) staticMethod(this.getClass(), "translateFromAlias", key), obj);
        return this;
    }

    public static String translateFromAlias(String key) {
        if (parent$_alias_names != null && parent$_alias_names.containsKey(key)) {
            return parent$_alias_names.get(key);
        }
        return key;
    }

    public static Map translateKeyForParams(Map params) {
        Map newParams = map();
        for (Object key : params.keySet()) {
            String keyStr = (String) key;
            newParams.put(translateFromAlias(keyStr), params.get(key));
        }
        params.clear();
        params.putAll(newParams);
        return params;
    }

    public static String translateToAlias(String key) {
        if (parent$_alias_names != null) {
            for (Map.Entry<String, String> entry : parent$_alias_names.entrySet()) {
                if (key.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return key;
    }

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
    protected static Map<String, String> parent$_alias_names;

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

    public boolean merge(Map item) {
        attributes.putAll(item);
        copyAllAttributesToPojoFields();
        return true;
    }

    /*
     # setting the collection name to store in.
     #
     # Example:
     #
     # <tt>Person.store_in(populdation)</tt>
     #
     # Warning: you should call this Class Method in subclass.
    */
    protected static DBCollection storeIn(String name) {

        parent$_collectionName = name;
        parent$_collection = mongoMongo.database().getCollection(name);
        return parent$_collection;
    }

    protected static Map parent$_validate_info;

    protected static void validate(String fieldName, Map validate) {
        if (parent$_validate_info == null) {
            parent$_validate_info = map();
        }
        parent$_validate_info = map(fieldName, validate);
    }

    protected static Map validate_info() {
        return parent$_validate_info;
    }

    /*
     index({ ssn: 1 }, { unique: true, name: "ssn_index" })
     */
    protected static void index(Map keys, Map indexOptions) {
        parent$_collection.ensureIndex(translateMapToDBObject(keys), translateMapToDBObject(indexOptions));
    }

    public static DBObject translateMapToDBObject(Map map) {
        DBObject query = new BasicDBObject();
        query.putAll(map);
        return query;
    }

    protected static void alias(String originalName, String aliasName) {
        if (parent$_alias_names == null) {
            parent$_alias_names = map(aliasName, originalName);
        } else {
            parent$_alias_names.put(aliasName, originalName);
        }
    }

    public static <T extends Document> T create(Map map) {
        throw new AutoGeneration();
    }

    public static <T extends Document> T create9(Map map) {
        throw new AutoGeneration();
    }


    public boolean save() {
        if (valid()) {
            return Save.execute(this);
        }
        return false;
    }

    public boolean save(boolean validate) {
        if (validate && valid()) {
            return Save.execute(this);
        }
        return false;
    }

    public boolean insert() {
        if (valid()) {
            return Insert.execute(this, false);
        }
        return false;
    }

    public boolean insert(boolean validate) {
        if (validate && valid()) {
            return Insert.execute(this, false);
        }
        return false;
    }


    public boolean update() {
        if (valid()) {
            return Update.execute(this, false);
        }
        return false;
    }

    public boolean update(boolean validate) {
        if (validate && valid()) {
            return Update.execute(this, false);
        }
        return false;
    }


    public final List<ValidateResult> validateResults = list();
    public final static List validateParses = list();

    public boolean valid() {
        runCallbacks(Callbacks.Callback.before_validation);
        if (validateResults.size() > 0) return false;
        for (Object validateParse : validateParses) {
            ((ValidateParse) validateParse).parse(this, this.validateResults);
        }
        runCallbacks(Callbacks.Callback.after_validation);
        return validateResults.size() == 0;
    }

    public void remove() {
        Delete.execute(this);
    }


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
            this.attributes().remove(name);
        }

        this.associationEmbedded().get(name).remove(child);
        this.save();
    }

    public void copyPojoFieldsToAllAttributes() {

        try {
            List<PropertyDescriptor> fromPd = Arrays.asList(Introspector.getBeanInfo(this.getClass())
                    .getPropertyDescriptors());

            for (PropertyDescriptor pd : fromPd) {
                if (pd.getName().equals("class")) continue;
                this.attributes.put((String) staticMethod(this.getClass(), "translateFromAlias", pd.getName()), pd.getReadMethod().invoke(this));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void copyAllAttributesToPojoFields() {
        DBObject newAttributes = new BasicDBObject();
        newAttributes.putAll(this.attributes());
        Map<String, String> tempMap = (Map<String, String>) staticField(this.getClass(), "parent$_alias_names");
        try {
            if (tempMap != null && tempMap.size() > 0) {
                for (Map.Entry<String, String> entry : tempMap.entrySet()) {
                    Object temp = this.attributes().get(entry.getValue());
                    if (temp != null)
                        newAttributes.put(entry.getKey(), temp);
                }
            }
            BeanUtils.copyProperties(this, newAttributes);
        } catch (Exception e) {
            e.printStackTrace();
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

    public Map reload() {
        attributes.putAll(collection().findOne(map("_id", attributes.get("_id"))).toMap());
        return attributes;
    }

    public Map attributes() {
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


    public String toString() {
        String attrs = join(iterate_map(attributes, new MapIterator<String, Object>() {
            @Override
            public Object iterate(String key, Object value) {
                if (value instanceof String) {
                    value = StringUtils.substring((String) value, 0, 50);
                }
                return format("{}: {}", key, value);
            }
        }), ",");
        return "#<" + this.getClass().getSimpleName() + " _id: " + id() + ", " + attrs + ">";
    }


    /*
    # Return the root +Document+ in the object graph. If the current +Document+
      # is the root object in the graph it will return self.
      def _root
        object = self
        while (object._parent) do object = object._parent; end
        object || self
      end
     */
    public Document _root() {
        Document doc = this;
        while (doc._parent != null) {
            doc = doc._parent;
        }
        return doc == null ? this : doc;
    }

    public boolean newRecord(boolean saved) {
        return new_record = saved;
    }

    //bind Criteria

    public static Criteria where(Map conditions) {
        //return new Criteria(Document.class).where(conditions);
        throw new AutoGeneration();
    }

    public static Criteria select(List fieldNames) {
        throw new AutoGeneration();
    }

    public static Criteria order(Map orderBy) {
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

    public static <T> T findById(Object id) {
        throw new AutoGeneration();
    }

    public static <T> List<T> find(List list) {
        throw new AutoGeneration();
    }

    public static <T> List<T> findAll() {
        throw new AutoGeneration();
    }


    protected Map<Callbacks.Callback, List<WowMethod>> callbacks = map();

    protected void collectCallback() {
        Method[] methods = this.getClass().getDeclaredMethods();
        if (callbacks.size() > 0) return;
        for (Method method : methods) {
            WowMethod wowMethod = new WowMethod(this, method);

            if (method.isAnnotationPresent(BeforeSave.class)) {
                putAndAdd(Callbacks.Callback.before_save, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeCreate.class)) {
                putAndAdd(Callbacks.Callback.before_create, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeDestroy.class)) {
                putAndAdd(Callbacks.Callback.before_destroy, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeUpdate.class)) {
                putAndAdd(Callbacks.Callback.before_update, wowMethod);
            }

            if (method.isAnnotationPresent(BeforeValidation.class)) {
                putAndAdd(Callbacks.Callback.before_validation, wowMethod);
            }


            if (method.isAnnotationPresent(AfterCreate.class)) {
                putAndAdd(Callbacks.Callback.after_create, wowMethod);
            }

            if (method.isAnnotationPresent(AfterDestory.class)) {
                putAndAdd(Callbacks.Callback.after_destroy, wowMethod);
            }

            if (method.isAnnotationPresent(AfterSave.class)) {
                putAndAdd(Callbacks.Callback.after_save, wowMethod);
            }

            if (method.isAnnotationPresent(AfterUpdate.class)) {
                putAndAdd(Callbacks.Callback.after_update, wowMethod);
            }

            if (method.isAnnotationPresent(AfterValidation.class)) {
                putAndAdd(Callbacks.Callback.after_validation, wowMethod);
            }

        }
    }

    public void runCallbacks(Callbacks.Callback callback, Object... params) {
        if (callbacks.size() == 0) {
            collectCallback();
        }
        List<WowMethod> wowMethods = callbacks.get(callback);
        if (wowMethods == null) return;
        for (WowMethod wowMethod : wowMethods) {
            wowMethod.invoke(params);
        }

    }

    private void putAndAdd(Callbacks.Callback key, WowMethod item) {
        if (callbacks.containsKey(key)) {
            callbacks.get(key).add(item);
        } else {
            callbacks.put(key, list(item));
        }
    }

    public static Criteria nativeQuery(String tableName) {
        return new Criteria(tableName);
    }

}
