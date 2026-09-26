package net.csdn.mongo.enhancer;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.Criteria;
import net.csdn.mongo.Document;
import net.csdn.mongo.MongoModelSchema;
import net.csdn.mongo.MongoMongo;
import org.junit.Assume;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MongoEnhancementLiveTest {

    private static final String PREFIX_KEY = "sf.mongo.collection.prefix";
    private static final String[] FIXTURE_DOCUMENTS = new String[]{
            "net.csdn.mongo.fixture.Folio",
            "net.csdn.mongo.fixture.Item",
            "net.csdn.mongo.fixture.Level1",
            "net.csdn.mongo.fixture.Level2",
            "net.csdn.mongo.fixture.Level3",
            "net.csdn.mongo.fixture.MetaLeft",
            "net.csdn.mongo.fixture.MetaRight",
            "net.csdn.mongo.fixture.Note",
            "net.csdn.mongo.fixture.Owner",
            "net.csdn.mongo.fixture.Page",
            "net.csdn.mongo.fixture.Record",
            "net.csdn.mongo.fixture.Volume"
    };
    private static final String[] CONFLICT_DOCUMENTS = new String[]{
            "net.csdn.mongo.conflict.LockedChild",
            "net.csdn.mongo.conflict.LockedParent"
    };
    private static final String[] SUFFIXES = new String[]{
            "record", "meta_left", "meta_right", "l1", "l2", "l3", "note", "item", "owner", "volume", "folio"
    };

    @Test
    public void enhancerDoesNotDefineClasses() {
        try {
            new MongoEnhancer(null).enhanceThisClass2(Collections.<CtClass>emptyList());
            fail("toClass path should be rejected");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.UNSUPPORTED, failure.getCategory());
            assertEquals("define", failure.getPhase());
            assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, failure.getRuleId());
            assertTrue(failure.getMessage().contains("Hot reload"));
        }
    }

    @Test
    public void disabledModuleDoesNotConnectOrScan() {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.fixture")
                .put("test.datasources.mongodb.disable", true)
                .put("test.datasources.mongodb.host", "127.0.0.1")
                .put("test.datasources.mongodb.port", 1)
                .build();
        MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                "test", settings, MongoEnhancementLiveTest.class);
        long started = System.nanoTime();
        MongoMongo.configure(configuration);
        long elapsedMs = (System.nanoTime() - started) / 1000000L;
        try {
            assertTrue("disabled configure tried to connect", elapsedMs < 1000L);
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.DISABLED, configuration.state());
            assertNull(configuration.client());
            assertNull(configuration.enhancementContext());
            assertNull(configuration.mongoMongo());
        } finally {
            configuration.close();
            configuration.close();
            assertTrue(configuration.isClosed());
        }
    }

    @Test
    public void connectFailureReleasesTheClient() {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.doesnotexist")
                .put("test.datasources.mongodb.disable", false)
                .put("test.datasources.mongodb.host", "127.0.0.1")
                .put("test.datasources.mongodb.port", 1)
                .put("test.datasources.mongodb.database", "sf_compat")
                .build();
        MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                "test", settings, MongoEnhancementLiveTest.class);
        try {
            configuration.configure();
            fail("port 1 should not accept a mongo connection");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertEquals("connect", failure.getPhase());
            assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, failure.getRuleId());
            assertNotNull(failure.getCause());
            assertFalse(failure.getMessage().contains("password"));
        } finally {
            try {
                assertEquals(MongoMongo.CSDNMongoConfiguration.State.FAILED, configuration.state());
                assertNotNull(configuration.enhancementContext());
                assertTrue(configuration.enhancementContext().isClosed());
                MongoMongo bound = configuration.mongoMongo();
                assertNotNull(bound);
                assertTrue(bound.isClientClosed());
                MongoClient client = configuration.client();
                try {
                    client.getDB("sf_compat").command(new BasicDBObject("ping", 1));
                    fail("closed mongo client still accepted a command");
                } catch (RuntimeException expected) {
                    assertNotNull(expected.getMessage());
                }
            } finally {
                configuration.close();
            }
        }
    }

    @Test
    public void uriConnectsHonorsUriDatabaseAndIgnoresDiscreteSettings() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration configuration = null;
        try {
            Class<?> anchor = anchor(loader);
            String uri = "mongodb://" + env.get("SF_COMPAT_MONGO_USER") + ":"
                    + env.get("SF_COMPAT_MONGO_PASSWORD")
                    + "@" + env.get("SF_COMPAT_MONGO_HOST") + ":" + env.get("SF_COMPAT_MONGO_PORT")
                    + "/" + env.get("SF_COMPAT_MONGO_DATABASE")
                    + "?replicaSet=" + env.get("SF_COMPAT_MONGO_REPLSET")
                    + "&authSource=" + env.get("SF_COMPAT_MONGO_AUTH_DB");
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", "net.csdn.mongo.fixture")
                    .put("test.datasources.mongodb.uri", uri)
                    // Discrete connection settings are stale on purpose: the URI
                    // must still be the only source parsed for this connection.
                    .put("test.datasources.mongodb.host", "192.0.2.1")
                    .put("test.datasources.mongodb.port", "not-a-port")
                    .put("test.datasources.mongodb.database", "unused_database")
                    .put("test.datasources.mongodb.username", "stale_discrete_user")
                    .put("test.datasources.mongodb.authenticationDatabase", "ignored_authdb")
                    .classLoader(anchor.getClassLoader())
                    .build();
            configuration = new MongoMongo.CSDNMongoConfiguration("test", settings, anchor);
            configuration.configure();
            assertEquals(env.get("SF_COMPAT_MONGO_DATABASE"), configuration.mongoMongo().dbName());
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CONFIGURED, configuration.state());
            Class<?> record = loader.loadClass("net.csdn.mongo.fixture.Record");
            assertEquals("Hello|Hello|true|Next|null", exerciseRecord(record, prefix + "-uri"));
            drop(configuration.mongoMongo(), prefix);
        } finally {
            if (configuration != null) {
                configuration.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void invalidUriFailsWithoutEchoingIt() throws Exception {
        String uri = "mongodb://u:the uri password@127.0.0.1:1/not a db";
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.fixture")
                .put("test.datasources.mongodb.uri", uri)
                .build();
        MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                "test", settings, MongoEnhancementLiveTest.class);
        try {
            configuration.configure();
            fail("an invalid uri must fail configure");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertEquals("connect", failure.getPhase());
            String message = String.valueOf(failure.getMessage());
            assertFalse(message.contains("the uri password"));
            assertFalse(message.contains("mongodb://u:"));
        } finally {
            configuration.close();
        }
    }

    @Test
    public void ruleFailureReleasesTheClient() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration configuration = null;
        try {
            Class<?> anchor = anchor(loader);
            configuration = configuration(env, anchor);
            configuration.registerRule(new BoomRule());
            try {
                configuration.configure();
                fail("boom rule should fail configuration");
            } catch (EnhancementFailure failure) {
                assertEquals("apply", failure.getPhase());
                assertEquals("mongo-it-boom", failure.getRuleId());
                assertNotNull(failure.getClassName());
                assertTrue(failure.getClassName().endsWith(".Record"));
                assertTrue(failure.getCause() instanceof IllegalStateException);
            }
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.FAILED, configuration.state());
            assertTrue(configuration.enhancementContext().isClosed());
            assertTrue(configuration.mongoMongo().isClientClosed());
        } finally {
            if (configuration != null) {
                configuration.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void enhancesLoadsAndReadsWritesOnMongo44() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loaderA = newLoader(classes);
        URLClassLoader loaderB = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration first = null;
        MongoMongo.CSDNMongoConfiguration second = null;
        try {
            Class<?> anchorA = anchor(loaderA);
            Class<?> anchorB = anchor(loaderB);
            first = configuration(env, anchorA);
            second = configuration(env, anchorB);
            MongoMongo.configure(first);
            MongoMongo.configure(second);
            assertNotSame(first.enhancementContext(), second.enhancementContext());
            assertNull(Document.mongoMongo);
            try {
                Document.mongo();
                fail("two open clients must not resolve through the static entry");
            } catch (EnhancementFailure failure) {
                assertEquals("resolve", failure.getPhase());
                assertTrue(failure.getMessage().contains("more than one"));
            }

            Class<?> recordA = loaderA.loadClass("net.csdn.mongo.fixture.Record");
            Class<?> recordB = loaderB.loadClass("net.csdn.mongo.fixture.Record");
            assertNotSame(recordA, recordB);
            executionsSkipNonDocuments(first, recordA.getName());

            assertSetterContract(recordA);
            String recordId = prefix + "-record";
            Object saved = insertRecord(recordA, recordId, "  Hello  ");
            assertEquals("Hello", call(saved, "getTitle"));
            Object found = callStatic(recordA, "findById", new Class<?>[]{Object.class}, new Object[]{recordId});
            assertNotNull(found);
            assertEquals("Hello", call(found, "getTitle"));
            call(found, "setTitle", new Class<?>[]{String.class}, new Object[]{"Next"});
            assertEquals(Boolean.TRUE, call(found, "update"));
            Object updated = callStatic(recordA, "findById", new Class<?>[]{Object.class}, new Object[]{recordId});
            assertEquals("Next", call(updated, "getTitle"));
            call(updated, "remove");
            assertNull(callStatic(recordA, "findById", new Class<?>[]{Object.class}, new Object[]{recordId}));

            Object aliased = callStatic(recordA, "create", new Class<?>[]{Map.class}, new Object[]{map("headline", "Alias")});
            assertEquals("Alias", call(aliased, "getTitle"));
            assertEquals("Alias", attributes(aliased).get("title"));
            assertFalse(attributes(aliased).containsKey("headline"));

            assertMetadataIsolated(loaderA);
            assertInheritance(loaderA, prefix);
            assertEmbedded(loaderA, prefix);
            assertAssociation(loaderA, prefix);

            assertNull(MongoMongo.current());
            DBCollection baked = (DBCollection) callStatic(recordA, "collection", new Class<?>[0], new Object[0]);
            assertSame(first.client(), baked.getDB().getMongoClient());
            EnhancementContext.Scope scope = second.mongoMongo().activate();
            try {
                assertSame(second.mongoMongo(), MongoMongo.current());
                DBCollection switched = (DBCollection) callStatic(recordA, "collection", new Class<?>[0], new Object[0]);
                assertSame(second.client(), switched.getDB().getMongoClient());
                Criteria criteria = new Criteria(documentClass(recordA));
                assertSame(second.client(), criteria.collection().getDB().getMongoClient());
                Criteria nativeQuery = new Criteria(prefix + "_record");
                assertSame(second.client(), nativeQuery.collection().getDB().getMongoClient());
            } finally {
                scope.close();
            }
            assertNull(MongoMongo.current());
            DBCollection restored = (DBCollection) callStatic(recordA, "collection", new Class<?>[0], new Object[0]);
            assertSame(first.client(), restored.getDB().getMongoClient());
            try {
                new Criteria(prefix + "_record").collection();
                fail("native query with two clients needs an active scope");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("more than one"));
            }
            assertAsyncActivation(second.mongoMongo());

            try {
                first.configure();
                fail("second configure on the same configuration");
            } catch (EnhancementFailure failure) {
                assertEquals("configure", failure.getPhase());
                assertTrue(failure.getMessage().contains("already configured"));
            }
            MongoMongo.CSDNMongoConfiguration again = configuration(env, anchorA);
            try {
                again.configure();
                fail("same loader cannot redefine enhanced classes");
            } catch (EnhancementFailure failure) {
                assertEquals("configure", failure.getPhase());
                assertTrue(failure.getMessage().contains("hot reload is not supported"));
            } finally {
                again.close();
            }

            String otherId = prefix + "-other";
            insertRecord(recordB, otherId, "FromB");
            MongoClient closedClient = first.client();
            first.close();
            first.close();
            assertTrue(first.enhancementContext().isClosed());
            assertFalse(second.enhancementContext().isClosed());
            assertTrue(first.mongoMongo().isClientClosed());
            try {
                closedClient.getDB(second.mongoMongo().dbName()).command(new BasicDBObject("ping", 1));
                fail("closed client still answered");
            } catch (RuntimeException expected) {
                assertNotNull(expected);
            }
            Object stillThere = callStatic(recordB, "findById", new Class<?>[]{Object.class}, new Object[]{otherId});
            assertEquals("FromB", call(stillThere, "getTitle"));
            assertSame(second.mongoMongo(), Document.mongo());
            MongoMongo.CSDNMongoConfiguration redefining = configuration(env, anchorA);
            try {
                redefining.configure();
                fail("closing the context does not allow hot reload");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("hot reload is not supported"));
            } finally {
                redefining.close();
            }
            assertNull(Document.mongoMongo);
            drop(second.mongoMongo(), prefix);
        } finally {
            if (second != null && second.mongoMongo() != null && !second.mongoMongo().isClientClosed()) {
                try {
                    drop(second.mongoMongo(), prefix);
                } catch (RuntimeException ignored) {
                    // the client may already be closed by the failure path
                }
            }
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
            loaderA.close();
            loaderB.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void activeEmptyOrDisabledContextDoesNotReadTheOtherDatabase() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration configured = null;
        EnhancementContext empty = null;
        EnhancementContext disabledContext = null;
        MongoMongo.CSDNMongoConfiguration disabled = null;
        try {
            Class<?> anchor = anchor(loader);
            configured = configuration(env, anchor);
            configured.configure();
            Class<?> record = loader.loadClass("net.csdn.mongo.fixture.Record");
            Injector injector = Guice.createInjector();
            MongoMongo.injector(injector);
            assertSame(injector, MongoMongo.injector());
            Settings settings = MongoMongo.settings();
            DBCollection baked = (DBCollection) callStatic(record, "collection", new Class<?>[0], new Object[0]);
            assertSame(configured.client(), baked.getDB().getMongoClient());
            assertSame(configured.mongoMongo(), Document.mongo());

            empty = EnhancementContext.open(loader);
            assertIsolatedFrom(record, prefix, empty);
            assertSame(injector, MongoMongo.injector());
            assertSame(settings, MongoMongo.settings());
            assertSame(configured.mongoMongo(), Document.mongo());
            DBCollection restored = (DBCollection) callStatic(record, "collection", new Class<?>[0], new Object[0]);
            assertSame(configured.client(), restored.getDB().getMongoClient());
            assertSame(configured.client(), new Criteria(prefix + "_record").collection().getDB().getMongoClient());

            disabledContext = EnhancementContext.open(loader);
            Settings disabledSettings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", "net.csdn.mongo.fixture")
                    .put("test.datasources.mongodb.disable", true)
                    .put("test.datasources.mongodb.host", "127.0.0.1")
                    .put("test.datasources.mongodb.port", 1)
                    .classLoader(loader)
                    .build();
            disabled = new MongoMongo.CSDNMongoConfiguration("test", disabledSettings, anchor)
                    .enhancementContext(disabledContext);
            MongoMongo.configure(disabled);
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.DISABLED, disabled.state());
            assertNull(disabled.client());
            assertFalse(disabledContext.isClosed());
            assertIsolatedFrom(record, prefix, disabledContext);
            assertSame(configured.mongoMongo(), Document.mongo());
            assertSame(settings, MongoMongo.settings());
            drop(configured.mongoMongo(), prefix);
        } finally {
            if (empty != null) {
                empty.close();
            }
            if (disabled != null) {
                disabled.close();
            }
            if (disabledContext != null) {
                disabledContext.close();
            }
            if (configured != null) {
                configured.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void externalContextCloseUnpublishesAndLeavesTheOtherApplication() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loaderA = newLoader(classes);
        URLClassLoader loaderB = newLoader(classes);
        EnhancementContext contextA = null;
        EnhancementContext contextB = null;
        MongoMongo.CSDNMongoConfiguration first = null;
        MongoMongo.CSDNMongoConfiguration second = null;
        try {
            Class<?> anchorA = anchor(loaderA);
            Class<?> anchorB = anchor(loaderB);
            contextA = EnhancementContext.open(loaderA);
            contextB = EnhancementContext.open(loaderB);
            first = configuration(env, anchorA).enhancementContext(contextA);
            second = configuration(env, anchorB).enhancementContext(contextB);
            first.configure();
            second.configure();
            Class<?> recordA = loaderA.loadClass("net.csdn.mongo.fixture.Record");
            Class<?> recordB = loaderB.loadClass("net.csdn.mongo.fixture.Record");
            String idA = prefix + "-ctx-a";
            String idB = prefix + "-ctx-b";
            insertRecord(recordA, idA, "FromA");
            insertRecord(recordB, idB, "FromB");

            MongoClient closedClient = first.client();
            contextA.close();
            assertTrue(contextA.isClosed());
            assertFalse(contextB.isClosed());
            assertTrue(first.isClosed());
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CONFIGURED, second.state());
            assertTrue(first.mongoMongo().isClientClosed());
            assertFalse(second.mongoMongo().isClientClosed());
            try {
                closedClient.getDB(second.mongoMongo().dbName()).command(new BasicDBObject("ping", 1));
                fail("context close left the mongo client usable");
            } catch (RuntimeException expected) {
                assertNotNull(expected);
            }
            first.close();
            assertTrue(first.isClosed());
            Object stillThere = callStatic(recordB, "findById", new Class<?>[]{Object.class}, new Object[]{idB});
            assertEquals("FromB", call(stillThere, "getTitle"));
            String idB2 = prefix + "-ctx-b2";
            insertRecord(recordB, idB2, "StillB");
            assertSame(second.mongoMongo(), Document.mongo());
            assertSame(second, MongoMongo.getMongoConfiguration());

            contextB.close();
            assertTrue(second.isClosed());
            try {
                Document.mongo();
                fail("closed configurations were still resolvable");
            } catch (EnhancementFailure failure) {
                assertEquals("resolve", failure.getPhase());
                assertTrue(failure.getMessage().contains("no mongo context is active"));
            }
            assertNull(MongoMongo.getMongoConfiguration());
            second.close();
        } finally {
            if (contextA != null && !contextA.isClosed()) {
                contextA.close();
            }
            if (contextB != null && !contextB.isClosed()) {
                contextB.close();
            }
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
            loaderA.close();
            loaderB.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void definedLoaderIsNotPinnedAndStillRejectsTheLiveLoader() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<ClassLoader>();
        int before = recordedDefinedLoaders();
        WeakReference<ClassLoader> ref = defineCloseAndDrop(classes, env, queue);
        boolean collected = awaitCollected(ref, queue);
        int after = recordedDefinedLoaders();
        deleteQuietly(classes);
        assertTrue(
                "loader still reachable after retries; recordedDefinedLoaders before="
                        + before + " after=" + after,
                collected);
        assertTrue(
                "defined-loader record outlived the loader before=" + before + " after=" + after,
                after <= before);
    }

    @Test
    public void equalButDistinctLoadersConfigureIndependently() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        ReferenceQueue<ClassLoader> queue = new ReferenceQueue<ClassLoader>();
        int before = recordedDefinedLoaders();
        try {
            EqualLoaderDrop drop = exerciseEqualLoaders(classes, env, queue, before);
            boolean collectedA = awaitCollected(drop.refA, queue);
            boolean collectedB = awaitCollected(drop.refB, queue);
            int after = recordedDefinedLoaders();
            assertTrue(
                    "equal loader still reachable before=" + before + " after=" + after,
                    collectedA && collectedB);
            assertTrue(
                    "equal-loader records outlived the loaders before=" + before + " after=" + after,
                    after <= before);
        } finally {
            deleteQuietly(classes);
        }
    }

    @Test
    public void ownedContextCloseFinishesAfterTheScopeEnds() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loaderA = newLoader(classes);
        URLClassLoader loaderB = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration first = null;
        MongoMongo.CSDNMongoConfiguration second = null;
        EnhancementContext.Scope scope = null;
        try {
            Class<?> anchorA = anchor(loaderA);
            Class<?> anchorB = anchor(loaderB);
            first = configuration(env, anchorA);
            second = configuration(env, anchorB);
            first.configure();
            second.configure();
            Class<?> recordB = loaderB.loadClass("net.csdn.mongo.fixture.Record");
            String idB = prefix + "-owned-b";
            insertRecord(recordB, idB, "FromB");

            MongoClient closedClient = first.client();
            String database = first.mongoMongo().dbName();
            scope = first.enhancementContext().activate();
            try {
                first.close();
                fail("owned close ignored the active scope");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
                assertEquals("close", failure.getPhase());
                assertTrue(failure.getMessage().contains("active scopes"));
                assertEquals(0, failure.getSuppressed().length);
            }
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CONFIGURED, first.state());
            assertFalse(first.isClosed());
            assertFalse(first.enhancementContext().isClosed());
            assertTrue(first.mongoMongo().isClientClosed());
            try {
                closedClient.getDB(database).command(new BasicDBObject("ping", 1));
                fail("rejected close left the mongo client usable");
            } catch (RuntimeException expected) {
                assertNotNull(expected);
            }
            try {
                Document.mongo();
                fail("active scope borrowed the other client");
            } catch (EnhancementFailure failure) {
                assertEquals("resolve", failure.getPhase());
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
            scope.close();
            scope = null;

            Object stillThere = callStatic(recordB, "findById", new Class<?>[]{Object.class}, new Object[]{idB});
            assertEquals("FromB", call(stillThere, "getTitle"));
            first.close();
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CLOSED, first.state());
            assertTrue(first.enhancementContext().isClosed());
            first.close();
            assertTrue(first.enhancementContext().isClosed());
            String idB2 = prefix + "-owned-b2";
            insertRecord(recordB, idB2, "StillB");
            assertSame(second.mongoMongo(), Document.mongo());
            drop(second.mongoMongo(), prefix);
        } finally {
            if (scope != null) {
                scope.close();
            }
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
            loaderA.close();
            loaderB.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void configurationCloseLeavesAnExternalContextOpen() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        EnhancementContext context = null;
        MongoMongo.CSDNMongoConfiguration configuration = null;
        EnhancementContext.Scope scope = null;
        try {
            Class<?> anchor = anchor(loader);
            context = EnhancementContext.open(loader);
            configuration = configuration(env, anchor).enhancementContext(context);
            configuration.configure();
            assertFalse(context.isClosed());
            scope = context.activate();
            configuration.close();
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CLOSED, configuration.state());
            assertFalse(context.isClosed());
            assertTrue(configuration.mongoMongo().isClientClosed());
            assertSame(context, configuration.enhancementContext());
            scope.close();
            scope = null;
            assertFalse(context.isClosed());
            context.close();
            assertTrue(context.isClosed());
            configuration.close();
            assertTrue(configuration.isClosed());
        } finally {
            if (scope != null) {
                scope.close();
            }
            if (context != null && !context.isClosed()) {
                context.close();
            }
            if (configuration != null) {
                configuration.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void clientCloseFailureStillReleasesTheOwnedContext() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader quietLoader = newLoader(classes);
        URLClassLoader busyLoader = newLoader(classes);
        CloseFailingConfiguration quiet = null;
        CloseFailingConfiguration busy = null;
        EnhancementContext.Scope scope = null;
        try {
            quiet = failingConfiguration(env, anchor(quietLoader));
            quiet.configure();
            MongoClient quietClient = quiet.client();
            String quietDatabase = quiet.mongoMongo().dbName();
            try {
                quiet.close();
                fail("client close failure was ignored");
            } catch (IllegalStateException expected) {
                assertEquals("client close failed", expected.getMessage());
                assertEquals(0, expected.getSuppressed().length);
            }
            assertEquals(1, quiet.closes.get());
            assertTrue(quiet.mongoMongo().isClientClosed());
            assertTrue(quiet.enhancementContext().isClosed());
            assertTrue(quiet.isClosed());
            try {
                quietClient.getDB(quietDatabase).command(new BasicDBObject("ping", 1));
                fail("failed client close left the client usable");
            } catch (RuntimeException expected) {
                assertNotNull(expected);
            }
            quiet.close();
            assertEquals(1, quiet.closes.get());

            busy = failingConfiguration(env, anchor(busyLoader));
            busy.configure();
            scope = busy.enhancementContext().activate();
            try {
                busy.close();
                fail("client close failure skipped reporting");
            } catch (IllegalStateException expected) {
                assertEquals("client close failed", expected.getMessage());
                assertEquals(1, expected.getSuppressed().length);
                Throwable suppressed = expected.getSuppressed()[0];
                assertTrue(suppressed instanceof EnhancementFailure);
                EnhancementFailure failure = (EnhancementFailure) suppressed;
                assertEquals("close", failure.getPhase());
                assertTrue(failure.getMessage().contains("active scopes"));
            }
            assertEquals(1, busy.closes.get());
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CONFIGURED, busy.state());
            assertFalse(busy.enhancementContext().isClosed());
            assertTrue(busy.mongoMongo().isClientClosed());
            scope.close();
            scope = null;
            busy.close();
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CLOSED, busy.state());
            assertTrue(busy.enhancementContext().isClosed());
            assertEquals(1, busy.closes.get());
            busy.close();
            assertEquals(1, busy.closes.get());
        } finally {
            if (scope != null) {
                scope.close();
            }
            if (quiet != null) {
                quiet.close();
            }
            if (busy != null) {
                busy.close();
            }
            quietLoader.close();
            busyLoader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void missingAssociationMetadataFailsAtConfigure() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader goodLoader = newLoader(classes);
        URLClassLoader brokenLoader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration good = null;
        MongoMongo.CSDNMongoConfiguration broken = null;
        try {
            Class<?> goodAnchor = anchor(goodLoader);
            good = configuration(env, goodAnchor);
            good.configure();
            Class<?> record = goodLoader.loadClass("net.csdn.mongo.fixture.Record");
            insertRecord(record, prefix + "-kept", "Kept");

            Class<?> brokenAnchor = Class.forName(
                    "net.csdn.mongo.miss.ServiceFrameworkPackageAnchor", false, brokenLoader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", "net.csdn.mongo.miss")
                    .put("test.datasources.mongodb.disable", false)
                    .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                    .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                    .classLoader(brokenLoader)
                    .build();
            broken = new MongoMongo.CSDNMongoConfiguration("test", settings, brokenAnchor);
            try {
                broken.configure();
                fail("association accessor without metadata was accepted");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("initialize", failure.getPhase());
                assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, failure.getRuleId());
                assertTrue(failure.getClassName().endsWith(".Ghost"));
                assertTrue(failure.getMessage().contains("missing"));
                assertTrue(failure.getMessage().contains("no metadata"));
            }
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.FAILED, broken.state());
            assertTrue(broken.mongoMongo().isClientClosed());
            assertTrue(broken.enhancementContext().isClosed());
            Object kept = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{prefix + "-kept"});
            assertEquals("Kept", call(kept, "getTitle"));
            drop(good.mongoMongo(), prefix);
        } finally {
            if (broken != null) {
                broken.close();
            }
            if (good != null) {
                good.close();
            }
            goodLoader.close();
            brokenLoader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void inheritedFinalAssociationFailsAtConfigure() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        URLClassLoader loader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration configuration = null;
        try {
            Class<?> anchor = Class.forName(
                    "net.csdn.mongo.conflict.ServiceFrameworkPackageAnchor", false, loader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", "net.csdn.mongo.conflict")
                    .put("test.datasources.mongodb.disable", false)
                    .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                    .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                    .classLoader(loader)
                    .build();
            configuration = new MongoMongo.CSDNMongoConfiguration("test", settings, anchor);
            try {
                configuration.configure();
                fail("final inherited association was copied");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("enhance", failure.getPhase());
                assertTrue(failure.getClassName().endsWith(".LockedChild"));
                assertTrue(failure.getMessage().contains("final"));
                assertTrue(failure.getMessage().contains("notes"));
            }
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.FAILED, configuration.state());
            assertNotNull(configuration.mongoMongo());
            assertTrue(configuration.mongoMongo().isClientClosed());
            assertTrue(configuration.enhancementContext().isClosed());
        } finally {
            if (configuration != null) {
                configuration.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void diagnosticsOptInRejectsContradictionsAndStaysIdleWhenDisabled() {
        try {
            new MongoMongo.CSDNMongoConfiguration("test", disabledSettings(), MongoEnhancementLiveTest.class)
                    .enhancementDiagnostics(null);
            fail("null diagnostics was accepted");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertTrue(failure.getMessage().contains("diagnostics are required"));
            assertFalse(failure.getMessage().contains("password"));
        }

        EnhancementDiagnostics idle = EnhancementDiagnostics.disabled();
        MongoMongo.CSDNMongoConfiguration disabledModule = new MongoMongo.CSDNMongoConfiguration(
                "test", disabledSettings(), MongoEnhancementLiveTest.class)
                .enhancementDiagnostics(idle);
        assertNull(disabledModule.configure());
        assertEquals(MongoMongo.CSDNMongoConfiguration.State.DISABLED, disabledModule.state());
        assertNull(disabledModule.enhancementContext());
        assertEquals(0, disabledModule.modelSchemaDigestComputations());
        assertFalse(idle.enabled());
        assertEquals(0, idle.hashComputations());
        assertEquals(0, idle.bytecodeReads());
        assertEquals(0, idle.methodInspections());
        assertEquals(0, idle.diskWrites());
        assertNull(idle.configVersion());
        assertNull(idle.schemaDigest());
        try {
            disabledModule.enhancementDiagnostics(EnhancementDiagnostics.disabled());
            fail("diagnostics changed after configure");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }

        File untouched = reportDir("disabled-must-not-write");
        EnhancementDiagnostics enabledButUnused = EnhancementDiagnostics.enabled(untouched);
        EnhancementContext agreed = EnhancementContext.open(
                MongoEnhancementLiveTest.class.getClassLoader(), enabledButUnused);
        try {
            MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                    "test", disabledSettings(), MongoEnhancementLiveTest.class)
                    .enhancementContext(agreed)
                    .enhancementDiagnostics(enabledButUnused);
            assertNull(configuration.configure());
            assertEquals(0, configuration.modelSchemaDigestComputations());
            assertEquals(0, enabledButUnused.hashComputations());
            assertNull(enabledButUnused.schemaDigest());
            assertFalse(untouched.exists());
        } finally {
            agreed.close();
            deleteQuietly(untouched);
        }

        File disagreedDir = reportDir("contradiction");
        EnhancementDiagnostics onTheContext = EnhancementDiagnostics.disabled();
        EnhancementDiagnostics other = EnhancementDiagnostics.enabled(disagreedDir);
        EnhancementContext context = EnhancementContext.open(
                MongoEnhancementLiveTest.class.getClassLoader(), onTheContext);
        MongoMongo.CSDNMongoConfiguration configuration = new MongoMongo.CSDNMongoConfiguration(
                "test", connectingSettings(), MongoEnhancementLiveTest.class)
                .enhancementContext(context)
                .enhancementDiagnostics(other);
        long started = System.nanoTime();
        try {
            try {
                configuration.configure();
                fail("contradictory diagnostics were accepted");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                assertEquals("configure", failure.getPhase());
                assertTrue(failure.getMessage().contains("disagree"));
                assertFalse(failure.getMessage().contains("password"));
                assertFalse(failure.getMessage().contains("jdbc:"));
            }
            long elapsedMs = (System.nanoTime() - started) / 1000000L;
            assertTrue("contradictory configuration tried to connect", elapsedMs < 1000L);
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.NEW, configuration.state());
            assertEquals(0, configuration.modelSchemaDigestComputations());
            assertEquals(0, other.hashComputations());
            assertFalse(disagreedDir.exists());
        } finally {
            context.close();
            configuration.close();
        }
    }

    @Test
    public void enabledAndDisabledCrudShareBehaviorAndReportRealOrigins() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        File reports = reportDir("success");
        URLClassLoader quietLoader = newLoader(classes);
        URLClassLoader loudLoader = newLoader(classes);
        MongoMongo.CSDNMongoConfiguration quiet = null;
        MongoMongo.CSDNMongoConfiguration loud = null;
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reports);
        try {
            Class<?> quietAnchor = anchor(quietLoader);
            Class<?> loudAnchor = anchor(loudLoader);
            quiet = configuration(env, quietAnchor);
            loud = configuration(env, loudAnchor).enhancementDiagnostics(diagnostics);
            quiet.configure();
            loud.configure();
            assertFalse(diagnostics.emitGeneratedSource());
            assertFalse(diagnostics.emitClassFiles());
            assertEquals(0, diagnostics.diskWrites());
            assertFalse(reports.exists());

            Class<?> quietRecord = quietLoader.loadClass("net.csdn.mongo.fixture.Record");
            Class<?> loudRecord = loudLoader.loadClass("net.csdn.mongo.fixture.Record");
            String quietTrace = exerciseRecord(quietRecord, prefix + "-off");
            String loudTrace = exerciseRecord(loudRecord, prefix + "-on");
            assertEquals("Hello|Hello|true|Next|null", quietTrace);
            assertEquals(quietTrace, loudTrace);

            EnhancementDiagnostics quietDiagnostics = quiet.enhancementContext().diagnostics();
            assertFalse(quietDiagnostics.enabled());
            assertEquals(0, quietDiagnostics.hashComputations());
            assertEquals(0, quietDiagnostics.bytecodeReads());
            assertEquals(0, quietDiagnostics.methodInspections());
            assertEquals(0, quietDiagnostics.diskWrites());
            assertEquals(0, quietDiagnostics.events().size());
            assertNull(quietDiagnostics.configVersion());
            assertNull(quietDiagnostics.schemaDigest());
            assertEquals(0, quiet.modelSchemaDigestComputations());

            assertTrue(diagnostics.enabled());
            assertTrue(diagnostics.hashComputations() > 0);
            assertTrue(diagnostics.bytecodeReads() > 0);
            assertTrue(diagnostics.methodInspections() > 0);
            assertEquals(1, loud.modelSchemaDigestComputations());
            String expectedDigest = digestOf(classes, FIXTURE_DOCUMENTS);
            assertEquals(expectedDigest, diagnostics.schemaDigest());
            assertEquals(MongoModelSchema.VERSION, diagnostics.configVersion());
            assertNoSecrets(expectedDigest, env);
            assertOrigins(diagnostics, loudRecord.getName(), expectedDigest);
            Class<?> level3 = loudLoader.loadClass("net.csdn.mongo.fixture.Level3");
            assertLevel3Associations(diagnostics, level3.getName());

            drop(loud.mongoMongo(), prefix);
            loud.close();
            assertTrue(diagnostics.diskWrites() > 0);
            assertTrue(diagnostics.flushed());
            File report = new File(reports, "report.txt");
            String text = readUtf8(report);
            assertTrue(text.startsWith("# enhancement-diagnostics v1\n"));
            assertTrue(text.contains("findById(Ljava/lang/Object;)Ljava/lang/Object;"));
            assertTrue(text.contains("rule=mongo-document"));
            assertTrue(text.contains("version=1"));
            assertTrue(text.contains("configVersion=" + MongoModelSchema.VERSION));
            assertTrue(text.contains("schemaDigest=" + expectedDigest));
            assertTrue(text.contains("custom%20getter"));
            assertTrue(text.contains("overload"));
            assertNoSecrets(text, env);
            assertFalse(new File(reports, "sources").isDirectory());
            assertFalse(new File(reports, "original").isDirectory());
            assertFalse(new File(reports, "enhanced").isDirectory());
        } finally {
            if (loud != null && loud.mongoMongo() != null && !loud.mongoMongo().isClientClosed()) {
                try {
                    drop(loud.mongoMongo(), prefix);
                } catch (RuntimeException ignored) {
                    // already closed
                }
            }
            if (quiet != null) {
                quiet.close();
            }
            if (loud != null) {
                loud.close();
            }
            quietLoader.close();
            loudLoader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void suppliedContextRestoresOrchestratorMetadata() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        File reports = reportDir("shared");
        URLClassLoader loader = newLoader(classes);
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reports);
        String orchestratorDigest = repeated('a', 64);
        diagnostics.noteSafeMetadata("app-rev-1", orchestratorDigest);
        diagnostics.recordApply(
                "earlier.OrmModel",
                "entity-mapping",
                2,
                0,
                1L,
                "boundary",
                Collections.<EnhancementDiagnostics.MethodChange>emptyList());
        EnhancementContext context = EnhancementContext.open(loader, diagnostics);
        MongoMongo.CSDNMongoConfiguration configuration = null;
        try {
            Class<?> anchor = anchor(loader);
            configuration = configuration(env, anchor)
                    .enhancementContext(context)
                    .enhancementDiagnostics(diagnostics);
            configuration.configure();
            assertFalse(configuration.enhancementContext().isClosed());
            assertSame(context, configuration.enhancementContext());
            assertEquals(1, configuration.modelSchemaDigestComputations());
            String modelDigest = digestOf(classes, FIXTURE_DOCUMENTS);
            assertFalse(modelDigest.equals(orchestratorDigest));
            assertEquals("app-rev-1", diagnostics.configVersion());
            assertEquals(orchestratorDigest, diagnostics.schemaDigest());

            Class<?> record = loader.loadClass("net.csdn.mongo.fixture.Record");
            assertEquals("Hello|Hello|true|Next|null", exerciseRecord(record, prefix + "-shared"));
            diagnostics.recordApply(
                    "later.Controller",
                    "controller-filter",
                    3,
                    0,
                    1L,
                    "boundary",
                    Collections.<EnhancementDiagnostics.MethodChange>emptyList());
            boolean sawMongo = false;
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if ("earlier.OrmModel".equals(event.className()) || "later.Controller".equals(event.className())) {
                    assertEquals("app-rev-1", event.configVersion());
                    assertEquals(orchestratorDigest, event.schemaDigest());
                    assertNoSecrets(String.valueOf(event.reason()), env);
                } else {
                    assertEquals("app-rev-1", event.configVersion());
                    assertEquals(modelDigest, event.schemaDigest());
                    assertFalse(MongoModelSchema.VERSION.equals(event.configVersion()));
                    sawMongo = true;
                }
            }
            assertTrue(sawMongo);
            EnhancementDiagnostics.MethodOrigin finder = diagnostics.originOf(
                    record.getName(), "findById(Ljava/lang/Object;)Ljava/lang/Object;");
            assertNotNull(finder);
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, finder.change());
            assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, finder.ruleId());
            assertEquals(1, finder.ruleVersion());
            drop(configuration.mongoMongo(), prefix);
        } finally {
            if (configuration != null) {
                configuration.close();
            }
            assertFalse(context.isClosed());
            context.close();
            loader.close();
            deleteQuietly(classes);
        }
    }

    @Test
    public void inheritedFinalAssociationFailureIsReported() throws Exception {
        requireLive();
        Map<String, String> env = credentials();
        String prefix = prefix();
        System.setProperty(PREFIX_KEY, prefix);
        File classes = compileFixtures();
        File reports = reportDir("failure");
        URLClassLoader loader = newLoader(classes);
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reports);
        MongoMongo.CSDNMongoConfiguration configuration = null;
        try {
            Class<?> anchor = Class.forName(
                    "net.csdn.mongo.conflict.ServiceFrameworkPackageAnchor", false, loader);
            Settings settings = ImmutableSettings.settingsBuilder()
                    .put("mode", "test")
                    .put("application.document", "net.csdn.mongo.conflict")
                    .put("test.datasources.mongodb.disable", false)
                    .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                    .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                    .classLoader(loader)
                    .build();
            configuration = new MongoMongo.CSDNMongoConfiguration("test", settings, anchor)
                    .enhancementDiagnostics(diagnostics);
            try {
                configuration.configure();
                fail("final inherited association was copied");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("enhance", failure.getPhase());
                assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, failure.getRuleId());
                assertTrue(failure.getClassName().endsWith(".LockedChild"));
                assertTrue(failure.getMessage().contains("final"));
                assertTrue(failure.getMessage().contains("notes"));
                assertNoSecrets(failure.getMessage(), env);
            }
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.FAILED, configuration.state());
            assertTrue(configuration.enhancementContext().isClosed());
            assertTrue(configuration.mongoMongo().isClientClosed());
            assertEquals(1, configuration.modelSchemaDigestComputations());
            String childName = null;
            String expectedDigest = digestOf(classes, CONFLICT_DOCUMENTS);
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if (event.className() == null || !event.className().endsWith(".LockedChild") || event.failure() == null) {
                    continue;
                }
                childName = event.className();
                assertEquals("apply", event.phase());
                assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, event.ruleId());
                assertEquals(1, event.ruleVersion());
                assertTrue(event.failure().contains("phase=enhance"));
                assertTrue(event.failure().contains(childName));
                assertTrue(event.failure().contains("rule=mongo-document"));
                assertTrue(event.failure().contains("final"));
                assertTrue(event.failure().contains("notes"));
                assertEquals(MongoModelSchema.VERSION, event.configVersion());
                assertEquals(expectedDigest, event.schemaDigest());
                assertNoSecrets(event.failure(), env);
            }
            assertNotNull(childName);
            String report = readUtf8(new File(reports, "report.txt"));
            assertTrue(report.contains("class=" + childName));
            assertTrue(report.contains("rule=mongo-document"));
            assertTrue(report.contains("phase=apply"));
            assertTrue(report.contains("phase%3denhance"));
            assertTrue(report.contains("configVersion=" + MongoModelSchema.VERSION));
            assertTrue(report.contains("schemaDigest=" + expectedDigest));
            assertNoSecrets(report, env);
            assertFalse(new File(reports, "sources").isDirectory());
            assertFalse(new File(reports, "original").isDirectory());
            assertFalse(new File(reports, "enhanced").isDirectory());
            configuration.close();
            assertEquals(MongoMongo.CSDNMongoConfiguration.State.CLOSED, configuration.state());
            configuration.close();
        } finally {
            if (configuration != null) {
                configuration.close();
            }
            loader.close();
            deleteQuietly(classes);
        }
    }

    private static void assertIsolatedFrom(Class<?> record, String prefix, EnhancementContext context) throws Exception {
        EnhancementContext.Scope scope = context.activate();
        try {
            assertNull(MongoMongo.current());
            assertNull(MongoMongo.getMongoConfiguration());
            try {
                callStatic(record, "collection", new Class<?>[0], new Object[0]);
                fail("active context without mongo used the baked collection");
            } catch (InvocationTargetException thrown) {
                assertTrue(thrown.getCause() instanceof EnhancementFailure);
                assertTrue(thrown.getCause().getMessage().contains("no mongo client"));
            }
            try {
                Document.mongo();
                fail("active context without mongo resolved another client");
            } catch (EnhancementFailure failure) {
                assertEquals("resolve", failure.getPhase());
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
            try {
                new Criteria(documentClass(record)).collection();
                fail("model criteria read another application");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
            try {
                new Criteria(prefix + "_record").collection();
                fail("native criteria read another application");
            } catch (EnhancementFailure failure) {
                assertEquals("resolve", failure.getPhase());
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
            try {
                MongoMongo.settings();
                fail("settings fell back to the other application");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
            try {
                MongoMongo.injector();
                fail("injector fell back to the other application");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("no mongo client"));
            }
        } finally {
            scope.close();
        }
    }

    private static EqualLoaderDrop exerciseEqualLoaders(
            File classes,
            Map<String, String> env,
            ReferenceQueue<ClassLoader> queue,
            int before) throws Exception {
        EqualLoader loaderA = new EqualLoader(new URL[]{classes.toURI().toURL()}, MongoEnhancementLiveTest.class.getClassLoader());
        EqualLoader loaderB = new EqualLoader(new URL[]{classes.toURI().toURL()}, MongoEnhancementLiveTest.class.getClassLoader());
        MongoMongo.CSDNMongoConfiguration first = null;
        MongoMongo.CSDNMongoConfiguration second = null;
        MongoMongo.CSDNMongoConfiguration again = null;
        try {
            assertEquals(loaderA, loaderB);
            assertNotSame(loaderA, loaderB);
            assertEquals(loaderA.hashCode(), loaderB.hashCode());
            Class<?> anchorA = anchor(loaderA);
            Class<?> anchorB = anchor(loaderB);
            first = configuration(env, anchorA);
            second = configuration(env, anchorB);
            first.configure();
            second.configure();
            int during = recordedDefinedLoaders();
            assertTrue(
                    "equal loaders collapsed into one record before=" + before + " during=" + during,
                    during >= before + 2);
            Class<?> recordA = loaderA.loadClass("net.csdn.mongo.fixture.Record");
            Class<?> recordB = loaderB.loadClass("net.csdn.mongo.fixture.Record");
            assertNotSame(recordA, recordB);
            first.close();
            again = configuration(env, anchorA);
            try {
                again.configure();
                fail("live loader was accepted after close");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("hot reload is not supported"));
            }
            second.close();
            Thread.sleep(200L);
        } finally {
            if (again != null) {
                again.close();
            }
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
            loaderA.close();
            loaderB.close();
        }
        EqualLoaderDrop drop = new EqualLoaderDrop(
                new WeakReference<ClassLoader>(loaderA, queue),
                new WeakReference<ClassLoader>(loaderB, queue));
        loaderA = null;
        loaderB = null;
        return drop;
    }

    private static WeakReference<ClassLoader> defineCloseAndDrop(
            File classes,
            Map<String, String> env,
            ReferenceQueue<ClassLoader> queue) throws Exception {
        URLClassLoader loader = newLoader(classes);
        try {
            Class<?> anchor = anchor(loader);
            MongoMongo.CSDNMongoConfiguration first = configuration(env, anchor);
            first.configure();
            first.close();
            MongoMongo.CSDNMongoConfiguration second = configuration(env, anchor);
            try {
                second.configure();
                fail("live loader was accepted after close");
            } catch (EnhancementFailure failure) {
                assertTrue(failure.getMessage().contains("hot reload is not supported"));
            } finally {
                second.close();
            }
            Thread.sleep(200L);
        } finally {
            loader.close();
        }
        WeakReference<ClassLoader> ref = new WeakReference<ClassLoader>(loader, queue);
        loader = null;
        return ref;
    }

    private static boolean awaitCollected(WeakReference<?> ref, ReferenceQueue<?> queue) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (ref.get() == null) {
                return true;
            }
            byte[] pressure = new byte[1024 * 512];
            pressure[0] = 1;
            System.gc();
            System.runFinalization();
            queue.poll();
            if (ref.get() == null) {
                return true;
            }
            Thread.sleep(50L);
        }
        return ref.get() == null;
    }

    private static int recordedDefinedLoaders() throws Exception {
        Method method = MongoMongo.class.getDeclaredMethod("recordedDefinedLoaders");
        method.setAccessible(true);
        return ((Integer) method.invoke(null)).intValue();
    }

    private static void assertAsyncActivation(final MongoMongo mongo) throws Exception {
        final AtomicBoolean sawNull = new AtomicBoolean();
        final AtomicBoolean sawMongo = new AtomicBoolean();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sawNull.set(MongoMongo.current() == null);
                    EnhancementContext.Scope scope = mongo.activate();
                    try {
                        sawMongo.set(MongoMongo.current() == mongo);
                    } finally {
                        scope.close();
                    }
                    if (MongoMongo.current() != null) {
                        throw new AssertionError("async scope leaked after close");
                    }
                } catch (Throwable thrown) {
                    error.set(thrown);
                }
            }
        });
        thread.start();
        thread.join();
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        assertTrue(sawNull.get());
        assertTrue(sawMongo.get());
        assertNull(MongoMongo.current());
    }

    private static void assertSetterContract(Class<?> record) throws Exception {
        assertNotNull(record.getDeclaredMethod("setTitle", String.class));
        assertNotNull(record.getDeclaredMethod("setTitle", Object.class));
        Object broken = record.getDeclaredConstructor().newInstance();
        try {
            call(broken, "setTitle", new Class<?>[]{String.class}, new Object[]{null});
            fail("null title should be rejected by the original setter");
        } catch (InvocationTargetException thrown) {
            assertTrue(thrown.getCause() instanceof IllegalArgumentException);
        }
        assertFalse(attributes(broken).containsKey("title"));
        assertEquals(Integer.valueOf(0), call(broken, "getWrites"));

        Object overload = record.getDeclaredConstructor().newInstance();
        call(overload, "setTitle", new Class<?>[]{Object.class}, new Object[]{"Zed"});
        assertEquals("Zed", call(overload, "getTitle"));
        assertEquals(Integer.valueOf(10), call(overload, "getWrites"));
        assertFalse(attributes(overload).containsKey("title"));

        Object trimmed = record.getDeclaredConstructor().newInstance();
        call(trimmed, "setTitle", new Class<?>[]{String.class}, new Object[]{"  Hello  "});
        assertEquals("Hello", call(trimmed, "getTitle"));
        assertEquals(Integer.valueOf(1), call(trimmed, "getWrites"));
        assertEquals("Hello", attributes(trimmed).get("title"));
    }

    private static Object insertRecord(Class<?> record, String id, String title) throws Exception {
        Object doc = record.getDeclaredConstructor().newInstance();
        call(doc, "id", new Class<?>[]{Object.class}, new Object[]{id});
        call(doc, "setTitle", new Class<?>[]{String.class}, new Object[]{title});
        Object inserted = call(doc, "insert");
        if (!Boolean.TRUE.equals(inserted)) {
            fail("insert returned " + inserted + " validate=" + field(doc, "validateResults"));
        }
        return doc;
    }

    private static void assertMetadataIsolated(ClassLoader loader) throws Exception {
        Class<?> left = loader.loadClass("net.csdn.mongo.fixture.MetaLeft");
        Class<?> right = loader.loadClass("net.csdn.mongo.fixture.MetaRight");
        Map leftNames = (Map) ReflectHelper.staticField(left, "parent$_alias_names");
        Map rightNames = (Map) ReflectHelper.staticField(right, "parent$_alias_names");
        assertNotNull(leftNames);
        assertNotNull(rightNames);
        assertNotSame(leftNames, rightNames);
        assertEquals("name", leftNames.get("leftAlias"));
        assertNull(leftNames.get("rightAlias"));
        assertEquals("name", rightNames.get("rightAlias"));
        assertNull(rightNames.get("leftAlias"));
    }

    private static void assertInheritance(ClassLoader loader, String prefix) throws Exception {
        Class<?> level1 = loader.loadClass("net.csdn.mongo.fixture.Level1");
        Class<?> level2 = loader.loadClass("net.csdn.mongo.fixture.Level2");
        Class<?> level3 = loader.loadClass("net.csdn.mongo.fixture.Level3");
        Class<?> noteClass = loader.loadClass("net.csdn.mongo.fixture.Note");
        Map first = (Map) ReflectHelper.staticField(level1, "parent$_alias_names");
        Map third = (Map) ReflectHelper.staticField(level3, "parent$_alias_names");
        assertNotSame(first, third);
        assertEquals("name", first.get("l1"));
        assertNull(first.get("l3"));
        assertEquals("name", third.get("l3"));
        assertNull(third.get("l1"));

        Map parentAssociations = (Map) ReflectHelper.staticField(level2, "parent$_associations");
        Map childAssociations = (Map) ReflectHelper.staticField(level3, "parent$_associations");
        assertNotSame(parentAssociations, childAssociations);
        assertNotNull(parentAssociations.get("notes"));
        assertNull(parentAssociations.get("extras"));
        assertNotNull(childAssociations.get("notes"));
        assertNotNull(childAssociations.get("extras"));
        assertNotSame(parentAssociations.get("notes"), childAssociations.get("notes"));

        assertEquals(level2, level2.getDeclaredMethod("notes").getDeclaringClass());
        assertEquals(level3, level3.getDeclaredMethod("notes").getDeclaringClass());

        String level2Id = prefix + "-l2";
        String level3Id = prefix + "-l3";
        Object level2Doc = level2.getDeclaredConstructor().newInstance();
        call(level2Doc, "id", new Class<?>[]{Object.class}, new Object[]{level2Id});
        Object level3Doc = level3.getDeclaredConstructor().newInstance();
        call(level3Doc, "id", new Class<?>[]{Object.class}, new Object[]{level3Id});

        insertNote(noteClass, prefix + "-parent-note", "parent-note", "level2_id", level2Id);
        insertNote(noteClass, prefix + "-leaf-note", "leaf-note", "leaf_note_id", level3Id);
        insertNote(noteClass, prefix + "-decoy-note", "decoy-note", "level2_id", level3Id);
        insertNote(noteClass, prefix + "-extra-note", "extra-note", "level3_id", level3Id);

        assertFoundText(level2.getDeclaredMethod("notes").invoke(level2Doc), "parent-note");
        assertFoundText(level3.getDeclaredMethod("notes").invoke(level3Doc), "leaf-note");
        assertFoundText(level3.getDeclaredMethod("extras").invoke(level3Doc), "extra-note");
        assertFoundText(level2.getDeclaredMethod("notes").invoke(level2Doc), "parent-note");

        Object parentNotes = parentAssociations.get("notes");
        childAssociations.put("only-child", "x");
        childAssociations.put("notes", "replaced");
        assertSame(parentNotes, parentAssociations.get("notes"));
        assertNull(parentAssociations.get("only-child"));
        assertNull(parentAssociations.get("extras"));
        assertFoundText(level2.getDeclaredMethod("notes").invoke(level2Doc), "parent-note");
    }

    private static void assertEmbedded(ClassLoader loader, String prefix) throws Exception {
        Class<?> volumeClass = loader.loadClass("net.csdn.mongo.fixture.Volume");
        Class<?> folioClass = loader.loadClass("net.csdn.mongo.fixture.Folio");
        assertEquals(volumeClass, volumeClass.getDeclaredMethod("pages").getDeclaringClass());
        assertEquals(folioClass, folioClass.getDeclaredMethod("pages").getDeclaringClass());
        assertEquals(folioClass, folioClass.getDeclaredMethod("plates").getDeclaringClass());

        Map volumeEmbedded = (Map) ReflectHelper.staticField(volumeClass, "parent$_associations_embedded");
        Map folioEmbedded = (Map) ReflectHelper.staticField(folioClass, "parent$_associations_embedded");
        assertNotSame(volumeEmbedded, folioEmbedded);
        assertNotNull(volumeEmbedded.get("pages"));
        assertNull(volumeEmbedded.get("plates"));
        assertNotNull(folioEmbedded.get("pages"));
        assertNotNull(folioEmbedded.get("plates"));

        assertEmbeddedText(volumeClass, "pages", prefix + "-volume", "volume-page");
        assertEmbeddedText(folioClass, "pages", prefix + "-folio", "folio-page");
        assertEmbeddedText(folioClass, "plates", prefix + "-folio-plates", "folio-plate");

        Object volumePages = volumeEmbedded.get("pages");
        folioEmbedded.remove("pages");
        folioEmbedded.put("only-folio", "x");
        assertSame(volumePages, volumeEmbedded.get("pages"));
        assertNull(volumeEmbedded.get("only-folio"));
        assertNull(volumeEmbedded.get("plates"));
        assertEmbeddedText(volumeClass, "pages", prefix + "-volume-again", "volume-again");
    }

    private static void insertNote(Class<?> noteClass, String id, String text, String foreignKey, String foreignValue) throws Exception {
        Object note = noteClass.getDeclaredConstructor().newInstance();
        call(note, "id", new Class<?>[]{Object.class}, new Object[]{id});
        call(note, "setText", new Class<?>[]{String.class}, new Object[]{text});
        call(note, "attr", new Class<?>[]{String.class, Object.class}, new Object[]{foreignKey, foreignValue});
        assertEquals(Boolean.TRUE, call(note, "insert"));
    }

    private static void assertFoundText(Object association, String text) throws Exception {
        List<?> found = (List<?>) association.getClass().getMethod("findAll").invoke(association);
        assertEquals(1, found.size());
        assertEquals(text, call(found.get(0), "getText"));
    }

    private static void assertEmbeddedText(Class<?> ownerClass, String association, String id, String text) throws Exception {
        Object owner = ownerClass.getDeclaredConstructor().newInstance();
        call(owner, "id", new Class<?>[]{Object.class}, new Object[]{id});
        Object link = ownerClass.getDeclaredMethod(association).invoke(owner);
        Map<String, Object> child = new HashMap<String, Object>();
        child.put("text", text);
        link.getClass().getMethod("build", Map.class).invoke(link, child);
        assertEquals(Boolean.TRUE, call(owner, "save"));
        Object loaded = callStatic(ownerClass, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        assertNotNull(loaded);
        Object loadedLink = loaded.getClass().getMethod(association).invoke(loaded);
        List<?> found = (List<?>) loadedLink.getClass().getMethod("find").invoke(loadedLink);
        assertEquals(1, found.size());
        assertEquals(text, call(found.get(0), "getText"));
    }

    private static void assertAssociation(ClassLoader loader, String prefix) throws Exception {
        Class<?> ownerClass = loader.loadClass("net.csdn.mongo.fixture.Owner");
        Class<?> itemClass = loader.loadClass("net.csdn.mongo.fixture.Item");
        Object owner = ownerClass.getDeclaredConstructor().newInstance();
        String ownerId = prefix + "-owner";
        call(owner, "id", new Class<?>[]{Object.class}, new Object[]{ownerId});
        call(owner, "setLabel", new Class<?>[]{String.class}, new Object[]{"library"});
        assertEquals(Boolean.TRUE, call(owner, "insert"));

        Object item = itemClass.getDeclaredConstructor().newInstance();
        call(item, "id", new Class<?>[]{Object.class}, new Object[]{prefix + "-item"});
        call(item, "setTitle", new Class<?>[]{String.class}, new Object[]{"book"});
        call(item, "attr", new Class<?>[]{String.class, Object.class}, new Object[]{"owner_id", ownerId});
        assertEquals(Boolean.TRUE, call(item, "insert"));

        Object association = ownerClass.getDeclaredMethod("items").invoke(owner);
        List<?> found = (List<?>) association.getClass().getMethod("findAll").invoke(association);
        assertEquals(1, found.size());
        assertEquals("book", call(found.get(0), "getTitle"));
    }

    private static void executionsSkipNonDocuments(MongoMongo.CSDNMongoConfiguration configuration, String recordName) {
        boolean sawRecord = false;
        List<EnhancementPlan.Execution> executions = configuration.enhancementContext().executions();
        for (int i = 0; i < executions.size(); i++) {
            EnhancementPlan.Execution execution = executions.get(i);
            String name = execution.className();
            assertFalse(name.endsWith(".NotADocument"));
            assertFalse(name.endsWith(".ServiceFrameworkPackageAnchor"));
            assertFalse(name.endsWith(".Names"));
            if (recordName.equals(name) && EnhancementRuleIds.MONGO_DOCUMENT.equals(execution.ruleId())) {
                sawRecord = true;
            }
        }
        assertTrue(sawRecord);
    }

    private static void drop(MongoMongo mongo, String prefix) {
        for (int i = 0; i < SUFFIXES.length; i++) {
            mongo.collection(prefix + "_" + SUFFIXES[i]).drop();
        }
    }

    private static MongoMongo.CSDNMongoConfiguration configuration(Map<String, String> env, Class<?> anchor) {
        return new MongoMongo.CSDNMongoConfiguration("test", mongoSettings(env, anchor), anchor);
    }

    private static CloseFailingConfiguration failingConfiguration(Map<String, String> env, Class<?> anchor) {
        return new CloseFailingConfiguration("test", mongoSettings(env, anchor), anchor);
    }

    private static Settings mongoSettings(Map<String, String> env, Class<?> anchor) {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.fixture")
                .put("test.datasources.mongodb.disable", false)
                .put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"))
                .classLoader(anchor.getClassLoader())
                .build();
    }

    private static Class<?> anchor(ClassLoader loader) throws Exception {
        return Class.forName("net.csdn.mongo.fixture.ServiceFrameworkPackageAnchor", false, loader);
    }

    private static URLClassLoader newLoader(File classes) throws Exception {
        return new URLClassLoader(new URL[]{classes.toURI().toURL()}, MongoEnhancementLiveTest.class.getClassLoader());
    }

    static boolean mongoRequested() {
        if (Boolean.parseBoolean(System.getProperty("sf.compat.mongo", "false"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("SF_COMPAT_MONGO"));
    }

    private static void requireLive() {
        Assume.assumeTrue("sf.compat.mongo is not enabled", mongoRequested());
    }

    private static String prefix() {
        return "sf_it_" + UUID.randomUUID().toString().replace("-", "");
    }

    static Map<String, String> credentials() throws Exception {
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("SF_COMPAT_ENV_FILE is not set");
        }
        File file = new File(path);
        if (!file.isFile()) {
            fail("SF_COMPAT_ENV_FILE is not a file");
        }
        Map<String, String> values = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq).trim(), line.substring(eq + 1));
            }
        } finally {
            reader.close();
        }
        requireKey(values, "SF_COMPAT_MONGO_HOST");
        requireKey(values, "SF_COMPAT_MONGO_PORT");
        requireKey(values, "SF_COMPAT_MONGO_USER");
        requireKey(values, "SF_COMPAT_MONGO_PASSWORD");
        requireKey(values, "SF_COMPAT_MONGO_AUTH_DB");
        requireKey(values, "SF_COMPAT_MONGO_DATABASE");
        requireKey(values, "SF_COMPAT_MONGO_REPLSET");
        if (!"sf_compat".equals(values.get("SF_COMPAT_MONGO_DATABASE"))) {
            fail("SF_COMPAT_MONGO_DATABASE is not sf_compat");
        }
        return values;
    }

    private static void requireKey(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.length() == 0) {
            fail(key + " is missing");
        }
    }

    private static File compileFixtures() throws Exception {
        File sourceRoot = fixtureRoot();
        File output = new File(System.getProperty("java.io.tmpdir"), "sf-mongo-fixtures-" + UUID.randomUUID());
        if (!output.mkdirs()) {
            fail("cannot create fixture output");
        }
        List<File> sources = new ArrayList<File>();
        collectJava(sourceRoot, sources);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            fail("this JDK has no compiler");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            List<String> options = new ArrayList<String>();
            options.add("-encoding");
            options.add("UTF-8");
            options.add("-classpath");
            options.add(System.getProperty("java.class.path"));
            options.add("-d");
            options.add(output.getAbsolutePath());
            String specification = System.getProperty("java.specification.version");
            if ("1.8".equals(specification)) {
                options.add("-source");
                options.add("8");
                options.add("-target");
                options.add("8");
            } else {
                options.add("--release");
                options.add("8");
            }
            Boolean ok = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    options,
                    null,
                    files.getJavaFileObjectsFromFiles(sources)).call();
            if (!Boolean.TRUE.equals(ok)) {
                fail("fixture compile failed " + diagnostics.getDiagnostics());
            }
        } finally {
            files.close();
        }
        return output;
    }

    private static void collectJava(File directory, List<File> sources) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        Arrays.sort(children);
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory()) {
                collectJava(child, sources);
            } else if (child.getName().endsWith(".java")) {
                sources.add(child);
            }
        }
    }

    private static File fixtureRoot() {
        File direct = new File("src/test/fixtures");
        if (direct.isDirectory()) {
            return direct;
        }
        File nested = new File("serviceframework-mongo/src/test/fixtures");
        if (nested.isDirectory()) {
            return nested;
        }
        fail("fixture sources not found");
        return direct;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(Object doc) throws Exception {
        return (Map<String, Object>) call(doc, "attributes");
    }

    private static Object field(Object target, String name) throws Exception {
        return target.getClass().getField(name).get(target);
    }

    private static Object call(Object target, String name) throws Exception {
        return call(target, name, new Class<?>[0], new Object[0]);
    }

    private static Object call(Object target, String name, Class<?>[] params, Object[] args) throws Exception {
        Method method = find(target.getClass(), name, params);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object callStatic(Class<?> type, String name, Class<?>[] params, Object[] args) throws Exception {
        Method method = find(type, name, params);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Method find(Class<?> type, String name, Class<?>[] params) throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    @SuppressWarnings("unchecked")
    private static Class<Document> documentClass(Class<?> type) {
        return (Class<Document>) type;
    }

    private static Map<String, Object> map(String key, String value) {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put(key, value);
        return values;
    }

    private static Settings disabledSettings() {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.doesnotexist")
                .put("test.datasources.mongodb.disable", true)
                .put("test.datasources.mongodb.host", "127.0.0.1")
                .put("test.datasources.mongodb.port", 1)
                .build();
    }

    private static Settings connectingSettings() {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("application.document", "net.csdn.mongo.doesnotexist")
                .put("test.datasources.mongodb.disable", false)
                .put("test.datasources.mongodb.host", "127.0.0.1")
                .put("test.datasources.mongodb.port", 1)
                .put("test.datasources.mongodb.database", "sf_compat")
                .build();
    }

    private static File reportDir(String kind) {
        File dir = new File("/tmp/sf-mongo-loader-close-report/mongo-diagnostics", kind + "-" + System.getProperty("java.specification.version", "unknown"));
        deleteQuietly(dir);
        return dir;
    }

    private static String digestOf(File classes, String[] names) throws Exception {
        ClassPool pool = new ClassPool(false);
        pool.appendClassPath(new LoaderClassPath(MongoEnhancementLiveTest.class.getClassLoader()));
        pool.insertClassPath(classes.getAbsolutePath());
        List<CtClass> types = new ArrayList<CtClass>();
        for (int i = 0; i < names.length; i++) {
            types.add(pool.get(names[i]));
        }
        return MongoModelSchema.digest(types);
    }

    private static String exerciseRecord(Class<?> record, String id) throws Exception {
        assertSetterContract(record);
        Object saved = insertRecord(record, id, "  Hello  ");
        Object savedTitle = call(saved, "getTitle");
        Object found = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        Object foundTitle = call(found, "getTitle");
        call(found, "setTitle", new Class<?>[]{String.class}, new Object[]{"Next"});
        Object updatedFlag = call(found, "update");
        Object updated = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        Object updatedTitle = call(updated, "getTitle");
        call(updated, "remove");
        Object gone = callStatic(record, "findById", new Class<?>[]{Object.class}, new Object[]{id});
        return savedTitle + "|" + foundTitle + "|" + updatedFlag + "|" + updatedTitle + "|" + gone;
    }

    private static void assertOrigins(EnhancementDiagnostics diagnostics, String recordName, String expectedDigest) {
        String original = diagnostics.originalHash(recordName);
        assertNotNull(original);
        assertEquals(64, original.length());
        assertNull(diagnostics.originOf(recordName, "getTitle()Ljava/lang/String;"));
        assertNull(diagnostics.originOf(recordName, "setTitle(Ljava/lang/Object;)V"));
        EnhancementDiagnostics.MethodOrigin setter = diagnostics.originOf(recordName, "setTitle(Ljava/lang/String;)V");
        EnhancementDiagnostics.MethodOrigin finder = diagnostics.originOf(recordName, "findById(Ljava/lang/Object;)Ljava/lang/Object;");
        EnhancementDiagnostics.MethodOrigin copied = diagnostics.originOf(
                recordName,
                "hasMany(Ljava/lang/String;Lnet/csdn/mongo/association/Options;)Lnet/csdn/mongo/association/HasManyAssociation;");
        assertNotNull(setter);
        assertNotNull(finder);
        assertNotNull(copied);
        assertEquals(EnhancementDiagnostics.MethodChange.REWRITTEN, setter.change());
        assertEquals(EnhancementDiagnostics.MethodChange.ADDED, finder.change());
        assertEquals(EnhancementDiagnostics.MethodChange.ADDED, copied.change());
        assertRule(setter);
        assertRule(finder);
        assertRule(copied);
        boolean sawApply = false;
        List<EnhancementDiagnostics.Event> events = diagnostics.eventsFor(recordName);
        for (int i = 0; i < events.size(); i++) {
            EnhancementDiagnostics.Event event = events.get(i);
            if (!"apply".equals(event.phase()) || !EnhancementRuleIds.MONGO_DOCUMENT.equals(event.ruleId())) {
                continue;
            }
            sawApply = true;
            assertEquals(original, event.originalSha256());
            assertEquals(MongoModelSchema.VERSION, event.configVersion());
            assertEquals(expectedDigest, event.schemaDigest());
            assertEquals(MongoDocumentRule.APPLY_REASON, event.reason());
            boolean sawSetter = false;
            boolean sawFinder = false;
            boolean sawCopy = false;
            List<EnhancementDiagnostics.MethodChange> changes = event.methodChanges();
            for (int j = 0; j < changes.size(); j++) {
                EnhancementDiagnostics.MethodChange change = changes.get(j);
                assertFalse(change.signature().equals("getTitle()Ljava/lang/String;"));
                assertFalse(change.signature().equals("setTitle(Ljava/lang/Object;)V"));
                if ("setTitle(Ljava/lang/String;)V".equals(change.signature())) {
                    assertEquals(EnhancementDiagnostics.MethodChange.REWRITTEN, change.change());
                    sawSetter = true;
                }
                if ("findById(Ljava/lang/Object;)Ljava/lang/Object;".equals(change.signature())) {
                    assertEquals(EnhancementDiagnostics.MethodChange.ADDED, change.change());
                    sawFinder = true;
                }
                if (copied.signature().equals(change.signature())) {
                    assertEquals(EnhancementDiagnostics.MethodChange.ADDED, change.change());
                    sawCopy = true;
                }
            }
            assertTrue(signatures(changes), sawSetter && sawFinder && sawCopy);
        }
        assertTrue(sawApply);
    }

    private static void assertLevel3Associations(EnhancementDiagnostics diagnostics, String className) {
        EnhancementDiagnostics.MethodOrigin extras = diagnostics.originOf(
                className, "extras()Lnet/csdn/mongo/association/Association;");
        EnhancementDiagnostics.MethodOrigin notes = diagnostics.originOf(
                className, "notes()Lnet/csdn/mongo/association/Association;");
        assertNotNull(extras);
        assertNotNull(notes);
        assertEquals(EnhancementDiagnostics.MethodChange.REWRITTEN, extras.change());
        assertEquals(EnhancementDiagnostics.MethodChange.ADDED, notes.change());
        assertRule(extras);
        assertRule(notes);
        assertNull(diagnostics.originOf(className, "getLeaf()Ljava/lang/String;"));
    }

    private static void assertRule(EnhancementDiagnostics.MethodOrigin origin) {
        assertEquals(EnhancementRuleIds.MONGO_DOCUMENT, origin.ruleId());
        assertEquals(1, origin.ruleVersion());
        assertEquals("apply", origin.phase());
    }

    private static String signatures(List<EnhancementDiagnostics.MethodChange> changes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(changes.get(i).change()).append(' ').append(changes.get(i).signature());
        }
        return builder.toString();
    }

    private static void assertNoSecrets(String report, Map<String, String> env) {
        String password = env == null ? null : env.get("SF_COMPAT_MONGO_PASSWORD");
        String host = env == null ? null : env.get("SF_COMPAT_MONGO_HOST");
        if ((password != null && password.length() > 0 && report.contains(password))
                || report.contains("mongodb://")
                || report.contains("mongodb+srv://")
                || report.contains("jdbc:")
                || report.contains("password=")
                || (host != null
                && !"127.0.0.1".equals(host)
                && !"localhost".equals(host)
                && report.contains(host))) {
            fail("diagnostics report contains credential material");
        }
    }

    private static String readUtf8(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }

    private static String repeated(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return true;
                }
            });
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteQuietly(children[i]);
                }
            }
        }
        file.delete();
    }

    private static final class EqualLoader extends URLClassLoader {
        private EqualLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualLoader;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    private static final class EqualLoaderDrop {
        private final WeakReference<ClassLoader> refA;
        private final WeakReference<ClassLoader> refB;

        private EqualLoaderDrop(WeakReference<ClassLoader> refA, WeakReference<ClassLoader> refB) {
            this.refA = refA;
            this.refB = refB;
        }
    }

    private static final class CloseFailingConfiguration extends MongoMongo.CSDNMongoConfiguration {
        private final AtomicInteger closes = new AtomicInteger();

        private CloseFailingConfiguration(String mode, Settings settings, Class anchor) {
            super(mode, settings, anchor);
        }

        @Override
        protected Throwable closeMongoClient(MongoClient client) {
            closes.incrementAndGet();
            Throwable failure = super.closeMongoClient(client);
            if (failure != null) {
                return failure;
            }
            return new IllegalStateException("client close failed");
        }
    }

    private static final class BoomRule implements EnhancementRule {
        @Override
        public String id() {
            return "mongo-it-boom";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList(EnhancementRuleIds.MONGO_DOCUMENT);
        }

        @Override
        public boolean matches(CtClass type, EnhancementContext context) {
            return type.getName().endsWith(".Record");
        }

        @Override
        public void apply(CtClass type, EnhancementContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
