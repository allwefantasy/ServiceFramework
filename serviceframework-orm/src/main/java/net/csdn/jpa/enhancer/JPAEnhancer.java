package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.ActiveORMEnhancer;
import net.csdn.jpa.OrmSession;

import java.io.DataInputStream;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:11
 */
public class JPAEnhancer extends ActiveORMEnhancer {

    private Settings settings;

    public JPAEnhancer(Settings settings) {
        this.settings = settings;
    }

    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        EnhancementContext context = OrmSession.current().context();
        CtClass ctClass = context.classPool().makeClass(dataInputStream);
        context.track(ctClass);
        if (!ModelClass.isModelSubclass(ctClass)) {
            return null;
        }
        return ctClass;
    }

    public List<ModelClass> enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        try {
            return OrmEnhancer.enhance(ctClasses);
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (RuntimeException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    null,
                    null,
                    "apply",
                    "ORM enhancement failed",
                    e);
        }
    }
}
