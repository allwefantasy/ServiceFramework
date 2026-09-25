package net.csdn.bootstrap.extension;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.FrameworkExtension;
import net.csdn.bootstrap.loader.impl.ModelLoader;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.MongoMongo;

import java.util.Collections;
import java.util.List;

/**
 * Mongo lifecycle. Loaded only when the datasource is enabled.
 * {@link MongoMongo#configure} registers a closer on the application enhancement
 * context. Closing that context unpublishes the client and closes it.
 * {@link #close()} also calls {@link MongoMongo.CSDNMongoConfiguration#close()},
 * which is idempotent with that closer. Disabled mode must not call
 * {@link MongoMongo#settings()} or {@link MongoMongo#injector()}.
 */
public final class MongoFrameworkExtension implements FrameworkExtension {

    public static final String CAPABILITY = "datasource.mongodb";

    private MongoMongo.CSDNMongoConfiguration configuration;

    @Override
    public String id() {
        return "mongo";
    }

    @Override
    public List<String> provides() {
        return Collections.singletonList(CAPABILITY);
    }

    @Override
    public boolean enabled(Settings settings, ApplicationContext context) {
        return !mongoDisabled(settings, context);
    }

    @Override
    public void validate(Settings settings, ApplicationContext context) {
        String packages = settings.get("application.document");
        if (packages == null || packages.trim().length() == 0) {
            throw failure("application.document is required when MongoDB is enabled");
        }
    }

    @Override
    public void register(Settings settings, ApplicationContext context) {
        configuration = new MongoMongo.CSDNMongoConfiguration(
                context.mode().name(),
                settings,
                context.marker());
        configuration.enhancementContext(context.enhancementContext());
        if (net.csdn.common.enhancer.ClassDefiner.ANCHOR_SIMPLE_NAME.equals(context.marker().getSimpleName())) {
            configuration.registerAnchor(context.marker());
        }
        try {
            MongoMongo.configure(configuration);
            ModelLoader.markIfRuleRan(context, EnhancementRuleIds.MONGO_DOCUMENT);
        } catch (RuntimeException thrown) {
            closeQuietly(thrown);
            throw thrown;
        }
    }

    @Override
    public void start(Settings settings, ApplicationContext context) {
        if (configuration == null || configuration.mongoMongo() == null) {
            return;
        }
        MongoMongo.injector(context.injector());
    }

    @Override
    public void close() {
        MongoMongo.CSDNMongoConfiguration current = configuration;
        configuration = null;
        if (current != null) {
            current.close();
        }
    }

    private void closeQuietly(RuntimeException primary) {
        MongoMongo.CSDNMongoConfiguration current = configuration;
        configuration = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (RuntimeException thrown) {
            primary.addSuppressed(thrown);
        }
    }

    public static boolean mongoDisabled(Settings settings, ApplicationContext context) {
        return Boolean.TRUE.equals(settings.getAsBoolean(
                context.mode().name() + ".datasources.mongodb.disable",
                Boolean.TRUE));
    }

    private static EnhancementFailure failure(String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                MongoFrameworkExtension.class.getName(),
                "mongo",
                "extension",
                detail,
                null);
    }
}
