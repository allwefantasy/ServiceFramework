package net.csdn.jpa.model;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.MappedSuperclass;

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
