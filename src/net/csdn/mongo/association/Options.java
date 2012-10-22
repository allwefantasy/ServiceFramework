package net.csdn.mongo.association;

import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:19
 */
public class Options {

    private Class klass;
    private String name;
    private Object parentKey;


    private Object foreignKey;

    public Options(Map attributes) {
        klass = (Class) attributes.get("kclass");
        name = (String) attributes.get("name");
        parentKey = attributes.get("parentKey");
        foreignKey = attributes.get("foreignKey");
    }


    public Object foreignKey() {
        return foreignKey;
    }

    public Options foreignKey(Object foreign_key) {
        this.foreignKey = foreign_key;
        return this;
    }

    public Class kClass() {
        return klass;
    }

    public Options kClass(Class klass) {
        this.klass = klass;
        return this;
    }

    public String name() {
        return name;
    }

    public Options name(String name) {
        this.name = name;
        return this;
    }

    public Object parentKey() {
        return parentKey;
    }

    public Options parentKey(Object parentKey) {
        this.parentKey = parentKey;
        return this;
    }
}
