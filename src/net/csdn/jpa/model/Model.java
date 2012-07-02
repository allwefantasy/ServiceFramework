package net.csdn.jpa.model;

import java.lang.reflect.Field;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:42
 */
public interface Model {

    public void save();

    public void delete();

    public void update();

    public Object key();


    public static class Property {

        public String name;
        public Class<?> type;
        public Field field;
        public boolean isSearchable;
        public boolean isMultiple;
        public boolean isRelation;
        public boolean isGenerated;
        public Class<?> relationType;

    }


}
