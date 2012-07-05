package net.csdn.enhancer;

import javassist.CtClass;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:38
 */
public interface BitEnhancer {
    public void enhance(CtClass ctClass) throws Exception;

}
