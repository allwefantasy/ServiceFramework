package net.csdn.bootstrap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.Translator;

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
