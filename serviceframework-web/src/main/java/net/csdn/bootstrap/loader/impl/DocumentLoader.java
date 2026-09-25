package net.csdn.bootstrap.loader.impl;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.extension.MongoFrameworkExtension;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.enhancer.ClassDefiner;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.MongoMongo;

/**
 * Compatibility entry for the Mongo lifecycle. Scanning and definition stay in
 * {@link MongoMongo#configure}. This loader does not enhance documents itself
 * and does not swallow failures. A disabled datasource is not configured, matching
 * {@link MongoFrameworkExtension}'s default rather than the driver's default.
 */
public class DocumentLoader implements Loader {

    @Override
    public void load(Settings settings) throws Exception {
        ApplicationContext application = ApplicationContext.require();
        if (MongoFrameworkExtension.mongoDisabled(settings, application)) {
            return;
        }
        MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                application.mode().name(),
                settings,
                application.marker());
        configuration.enhancementContext(application.enhancementContext());
        if (ClassDefiner.ANCHOR_SIMPLE_NAME.equals(application.marker().getSimpleName())) {
            configuration.registerAnchor(application.marker());
        }
        MongoMongo.configure(configuration);
        ModelLoader.markIfRuleRan(application, EnhancementRuleIds.MONGO_DOCUMENT);
    }
}
