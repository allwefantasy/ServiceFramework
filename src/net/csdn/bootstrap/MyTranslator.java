package net.csdn.bootstrap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.Translator;
import net.csdn.ServiceFramwork;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.Enhancer;
import net.csdn.jpa.enhancer.JPAEnhancer;

/**
 * User: WilliamZhu
 * Date: 12-7-12
 * Time: 上午7:15
 */
public class MyTranslator implements Translator {
    @Override
    public void start(ClassPool classPool) throws NotFoundException, CannotCompileException {

    }

    @Override
    public void onLoad(ClassPool classPool, String s) throws NotFoundException, CannotCompileException {
        System.out.println(s);
    }
}
