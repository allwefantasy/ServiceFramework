package net.csdn.enhancer;

import net.csdn.jpa.enhancer.ModelClass;

import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:38
 */
public interface BitEnhancer {
    public void enhance(List<ModelClass> modelClasses) throws Exception;

}
