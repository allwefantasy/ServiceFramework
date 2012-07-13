package net.csdn.jpa.model;

import java.lang.reflect.Field;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:42
 */
public interface GenericModel {

    public boolean save();

    public void delete();

    public boolean update();

    public boolean refresh();

    public Object key();

}
