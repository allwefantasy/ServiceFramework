package net.csdn.mongo.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.Settings;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Legacy entry. Enhancement goes through {@link MongoDocumentRule} on the active
 * context. This class does not call {@code toClass}; a second definition of a
 * class in the same loader is not a hot reload.
 */
public class MongoEnhancer extends Enhancer {

    public MongoEnhancer(Settings settings) {
    }

    @Override
    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        EnhancementContext context = EnhancementContext.currentOrNull();
        if (context == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    EnhancementRuleIds.MONGO_DOCUMENT,
                    "enhance",
                    "MongoEnhancer requires an active EnhancementContext; MongoMongo.configure owns that context",
                    null);
        }
        CtClass ctClass;
        try {
            ctClass = context.classPool().makeClass(dataInputStream);
        } catch (IOException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.SCAN,
                    null,
                    EnhancementRuleIds.MONGO_DOCUMENT,
                    "scan",
                    "cannot read class file",
                    e);
        }
        context.track(ctClass);
        MongoDocumentRule rule = new MongoDocumentRule();
        if (!rule.matches(ctClass, context)) {
            return ctClass;
        }
        rule.apply(ctClass, context);
        return ctClass;
    }

    @Override
    public void enhanceThisClass2(List<CtClass> ctClasses) {
        throw new EnhancementFailure(
                EnhancementFailure.Category.UNSUPPORTED,
                null,
                EnhancementRuleIds.MONGO_DOCUMENT,
                "define",
                "MongoEnhancer does not call toClass. ClassDefiner defines each class once after enhancement. Hot reload is not supported.",
                null);
    }
}
