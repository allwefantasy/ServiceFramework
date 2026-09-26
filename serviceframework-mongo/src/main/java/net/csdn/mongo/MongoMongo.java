package net.csdn.mongo;

import com.google.inject.Injector;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import net.csdn.common.enhancer.ClassDefiner;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.enhancer.EnhancementRules;
import net.csdn.common.enhancer.StartupPhaseTrace;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.enhancer.MongoDocumentRule;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One Mongo client owned by one {@link CSDNMongoConfiguration}.
 * The legacy static methods follow the active {@link EnhancementContext}, or the
 * only open configuration. They do not switch {@link Document#mongoMongo}.
 */
public class MongoMongo {

    public static final String CONTEXT_ATTRIBUTE = "net.csdn.mongo.MongoMongo";

    private static final Object OPEN_LOCK = new Object();
    private static final Set<MongoMongo> OPEN = Collections.newSetFromMap(new IdentityHashMap<MongoMongo, Boolean>());
    /**
     * Live loaders only, compared by reference. Two loaders that
     * {@code equals} each other are still different defining loaders.
     * The record is weak, so a collected loader is not pinned. Closing a
     * configuration does not remove it: the same live loader must still be
     * rejected, because the JVM will not redefine those classes.
     */
    private static final WeakIdentityLoaders DEFINED_LOADERS = new WeakIdentityLoaders();

    private static final CSLogger logger = Loggers.getLogger(MongoMongo.class);

    private final MongoClient mongo;
    private final String dbName;
    private final CSDNMongoConfiguration configuration;
    private final AtomicBoolean clientClosed = new AtomicBoolean();

    private MongoMongo(MongoClient mongo, String dbName, CSDNMongoConfiguration configuration) {
        this.mongo = mongo;
        this.dbName = dbName;
        this.configuration = configuration;
    }

    /**
     * Configures and, when the module is enabled, connects, enhances and defines.
     * A disabled datasource does not open a client and does not scan.
     * The returned client is also {@link CSDNMongoConfiguration#mongoMongo()}.
     */
    public static void configure(CSDNMongoConfiguration configuration) {
        if (configuration == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, null, "configure", "configuration is required", null);
        }
        configuration.configure();
    }

    public static MongoMongo current() {
        EnhancementContext context = EnhancementContext.currentOrNull();
        if (context == null) {
            return null;
        }
        Object value = context.getAttribute(CONTEXT_ATTRIBUTE);
        return value instanceof MongoMongo ? (MongoMongo) value : null;
    }

    /**
     * Active scope, otherwise the single usable client. An active context with
     * no mongo client does not fall back to another application. A closed
     * client is not that single client. Two usable clients and no scope is a
     * lifecycle error: pass the context to the other thread and {@link #activate()} it there.
     */
    public static MongoMongo resolve() {
        if (EnhancementContext.currentOrNull() != null) {
            MongoMongo bound = usable(current());
            if (bound == null) {
                throw failure(
                        EnhancementFailure.Category.LIFECYCLE,
                        null,
                        "resolve",
                        "the active enhancement context has no mongo client",
                        null);
            }
            return bound;
        }
        List<MongoMongo> open = usableOpen();
        if (open.size() == 1) {
            return open.get(0);
        }
        throw failure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                "resolve",
                open.isEmpty()
                        ? "no mongo context is active"
                        : "more than one mongo context is open; activate the context on this thread",
                null);
    }

    /**
     * Same selection as {@link #resolve()}, but an active context with no mongo
     * client, or zero or several usable clients and no scope, returns null.
     */
    public static CSDNMongoConfiguration getMongoConfiguration() {
        if (EnhancementContext.currentOrNull() != null) {
            MongoMongo bound = usable(current());
            return bound == null ? null : bound.configuration;
        }
        List<MongoMongo> open = usableOpen();
        if (open.size() != 1) {
            return null;
        }
        return open.get(0).configuration;
    }

    public static String mode() {
        return configurationOrFail("mode").mode;
    }

    public static Settings settings() {
        return configurationOrFail("settings").settings;
    }

    public static void injector(Injector injector) {
        configurationOrFail("injector").injector = injector;
    }

    public static Injector injector() {
        return configurationOrFail("injector").injector;
    }

    public static ClassPool classPool() {
        EnhancementContext context = configurationOrFail("classPool").context;
        if (context == null || context.isClosed()) {
            throw failure(EnhancementFailure.Category.LIFECYCLE, null, "classPool", "enhancement context is closed", null);
        }
        return context.classPool();
    }

    public EnhancementContext.Scope activate() {
        if (configuration.context == null || configuration.context.isClosed()) {
            throw failure(EnhancementFailure.Category.LIFECYCLE, null, "activate", "enhancement context is closed", null);
        }
        return configuration.context.activate();
    }

    public EnhancementContext context() {
        return configuration.context;
    }

    public CSDNMongoConfiguration configuration() {
        return configuration;
    }

    public MongoClient mongo() {
        return mongo;
    }

    public String dbName() {
        return dbName;
    }

    public DB database() {
        return mongo.getDB(dbName);
    }

    public DBCollection collection(String tableName) {
        return database().getCollection(tableName);
    }

    public void close() {
        configuration.close();
    }

    public boolean isClientClosed() {
        return clientClosed.get();
    }

    /**
     * At most one driver close. A failure is returned so the caller can still
     * close an owned context; a later attempt does not close the client again.
     */
    private Throwable closeClient() {
        if (!clientClosed.compareAndSet(false, true)) {
            return null;
        }
        try {
            return configuration.closeMongoClient(mongo);
        } catch (Throwable thrown) {
            return thrown;
        }
    }

    private static CSDNMongoConfiguration configurationOrFail(String phase) {
        if (EnhancementContext.currentOrNull() != null && getMongoConfiguration() == null) {
            throw failure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    phase,
                    "the active enhancement context has no mongo client",
                    null);
        }
        CSDNMongoConfiguration configuration = getMongoConfiguration();
        if (configuration == null) {
            throw failure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    phase,
                    "no single mongo context is active; activate the context on this thread",
                    null);
        }
        return configuration;
    }

    private static MongoMongo usable(MongoMongo candidate) {
        if (candidate == null || candidate.isClientClosed() || candidate.configuration.isClosed()) {
            return null;
        }
        return candidate;
    }

    private static List<MongoMongo> usableOpen() {
        synchronized (OPEN_LOCK) {
            List<MongoMongo> usable = new ArrayList<MongoMongo>();
            for (MongoMongo candidate : OPEN) {
                if (usable(candidate) != null) {
                    usable.add(candidate);
                }
            }
            return usable;
        }
    }

    static int recordedDefinedLoaders() {
        synchronized (OPEN_LOCK) {
            return DEFINED_LOADERS.size();
        }
    }

    private static void publish(MongoMongo mongo) {
        synchronized (OPEN_LOCK) {
            OPEN.add(mongo);
        }
    }

    private static void unpublish(MongoMongo mongo) {
        if (mongo == null) {
            return;
        }
        synchronized (OPEN_LOCK) {
            OPEN.remove(mongo);
        }
    }

    private static boolean loaderAlreadyDefined(ClassLoader loader) {
        synchronized (OPEN_LOCK) {
            return DEFINED_LOADERS.contains(loader);
        }
    }

    private static void markDefined(ClassLoader loader) {
        synchronized (OPEN_LOCK) {
            DEFINED_LOADERS.add(loader);
        }
    }

    private static EnhancementFailure failure(EnhancementFailure.Category category, String className, String phase, String detail, Throwable cause) {
        return new EnhancementFailure(category, className, EnhancementRuleIds.MONGO_DOCUMENT, phase, detail, cause);
    }

    private static EnhancementFailure connectFailure(Throwable thrown) {
        if (thrown instanceof EnhancementFailure) {
            return (EnhancementFailure) thrown;
        }
        return failure(EnhancementFailure.Category.CONFIGURATION, null, "connect", "mongo client failed", thrown);
    }

    public static class CSDNMongoConfiguration {
        public enum State {
            NEW,
            DISABLED,
            CONFIGURED,
            FAILED,
            CLOSED
        }

        private final Settings settings;
        private final Class classLoader;
        private final String mode;
        private final List<EnhancementRule> extraRules = new ArrayList<EnhancementRule>();
        private final List<Class<?>> anchors = new ArrayList<Class<?>>();
        /** Client unpublish and close have run. An owned context may still be open. */
        private final AtomicBoolean resourcesReleased = new AtomicBoolean();
        private Injector injector;
        private EnhancementContext suppliedContext;
        private EnhancementDiagnostics suppliedDiagnostics;
        private EnhancementContext context;
        private boolean ownsContext;
        /** Times this configuration hashed the declared model schema. Stays 0 while diagnostics are off. */
        private int modelSchemaDigestComputations;
        private MongoMongo mongoMongo;
        private Closeable clientCloseable;
        private State state = State.NEW;

        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader) {
            this(mode, settings, classLoader, null);
        }

        /**
         * {@code classPool} is accepted so existing callers still compile. Enhancement
         * uses the context pool, not this argument, so two applications do not share
         * one Javassist pool.
         */
        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader, ClassPool classPool) {
            this.mode = mode;
            this.settings = settings;
            this.classLoader = classLoader;
        }

        public CSDNMongoConfiguration enhancementContext(EnhancementContext context) {
            this.suppliedContext = context;
            return this;
        }

        /**
         * Opt in to diagnostics for the context this configuration owns.
         * Omit this call, or pass {@link EnhancementDiagnostics#disabled()}, and
         * startup does not hash the model or write a report. A supplied context
         * already carries its own diagnostics; passing a different instance is
         * rejected. This method does not accept {@link Settings}.
         */
        public CSDNMongoConfiguration enhancementDiagnostics(EnhancementDiagnostics diagnostics) {
            if (diagnostics == null) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "configure", "diagnostics are required", null);
            }
            synchronized (this) {
                if (state != State.NEW) {
                    throw failure(
                            EnhancementFailure.Category.LIFECYCLE,
                            null,
                            "configure",
                            "diagnostics are fixed before configure",
                            null);
                }
                this.suppliedDiagnostics = diagnostics;
            }
            return this;
        }

        /**
         * How many times {@link #configure()} hashed the declared model schema.
         * Disabled diagnostics leave this at zero because the digest is not computed.
         */
        public synchronized int modelSchemaDigestComputations() {
            return modelSchemaDigestComputations;
        }

        public CSDNMongoConfiguration registerRule(EnhancementRule rule) {
            if (rule == null) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "register", "rule is required", null);
            }
            extraRules.add(rule);
            return this;
        }

        public CSDNMongoConfiguration registerAnchor(Class<?> anchor) {
            if (anchor == null) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "register-anchor", "anchor is required", null);
            }
            anchors.add(anchor);
            return this;
        }

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings ignored) {
            throw failure(EnhancementFailure.Category.UNSUPPORTED, null, "settings", "settings are fixed at construction", null);
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String ignored) {
            throw failure(EnhancementFailure.Category.UNSUPPORTED, null, "mode", "mode is fixed at construction", null);
        }

        public Class getClassLoader() {
            return classLoader;
        }

        public Injector getInjector() {
            return injector;
        }

        public void setInjector(Injector injector) {
            this.injector = injector;
        }

        public ClassPool getClassPool() {
            if (context == null || context.isClosed()) {
                return null;
            }
            return context.classPool();
        }

        public void setClassPool(ClassPool ignored) {
            throw failure(EnhancementFailure.Category.UNSUPPORTED, null, "classPool", "the enhancement context owns the class pool", null);
        }

        public EnhancementContext enhancementContext() {
            return context;
        }

        public MongoMongo mongoMongo() {
            return mongoMongo;
        }

        public MongoClient client() {
            return mongoMongo == null ? null : mongoMongo.mongo;
        }

        public State state() {
            return state;
        }

        public boolean isClosed() {
            return state == State.CLOSED;
        }

        public synchronized MongoMongo configure() {
            if (state == State.CONFIGURED) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "configuration is already configured", null);
            }
            if (state == State.CLOSED) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "configuration is closed", null);
            }
            if (state == State.FAILED) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "configuration already failed; create a new configuration", null);
            }
            if (state == State.DISABLED) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "configuration already completed because mongodb is disabled", null);
            }
            if (settings == null) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "configure", "settings are required", null);
            }
            rejectContradictoryDiagnostics();
            if (disabled()) {
                state = State.DISABLED;
                return null;
            }
            ClassLoader loader = targetLoader();
            String documentPackage = documentPackage();
            if (loaderAlreadyDefined(loader)) {
                throw failure(
                        EnhancementFailure.Category.CONFLICT,
                        null,
                        "configure",
                        "classes were already defined in this loader; hot reload is not supported",
                        null);
            }
            EnhancementContext.Scope scope = null;
            try {
                openContext(loader);
                connect(loader);
                scope = context.activate();
                loadDocuments(loader, documentPackage);
                loadValidators();
                publish(mongoMongo);
                state = State.CONFIGURED;
                return mongoMongo;
            } catch (Throwable thrown) {
                state = State.FAILED;
                if (scope != null) {
                    try {
                        scope.close();
                    } catch (Throwable suppressed) {
                        thrown.addSuppressed(suppressed);
                    }
                    scope = null;
                }
                try {
                    releaseQuietly();
                } catch (Throwable suppressed) {
                    thrown.addSuppressed(suppressed);
                }
                if (thrown instanceof EnhancementFailure) {
                    throw (EnhancementFailure) thrown;
                }
                if (thrown instanceof Error) {
                    throw (Error) thrown;
                }
                throw failure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        null,
                        "configure",
                        thrown.getMessage() == null ? thrown.getClass().getName() : thrown.getMessage(),
                        thrown);
            } finally {
                if (scope != null) {
                    scope.close();
                }
            }
        }

        public void close() {
            finish(true);
        }

        /**
         * One driver close. Return the failure instead of throwing it so
         * configuration cleanup can continue. The client is closed at most once.
         */
        protected Throwable closeMongoClient(MongoClient client) {
            try {
                client.close();
                return null;
            } catch (Throwable thrown) {
                return thrown;
            }
        }

        private boolean disabled() {
            return Boolean.TRUE.equals(settings.getAsBoolean(mode + ".datasources.mongodb.disable", Boolean.FALSE));
        }

        private ClassLoader targetLoader() {
            if (classLoader == null || classLoader.getClassLoader() == null) {
                throw failure(
                        EnhancementFailure.Category.CONFIGURATION,
                        classLoader == null ? null : classLoader.getName(),
                        "configure",
                        "target class loader is required",
                        null);
            }
            return classLoader.getClassLoader();
        }

        private String documentPackage() {
            String value = settings.get("application.document");
            if (value == null || value.trim().length() == 0) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "scan", "application.document is required", null);
            }
            return value.trim();
        }

        private void rejectContradictoryDiagnostics() {
            if (suppliedDiagnostics == null || suppliedContext == null) {
                return;
            }
            if (suppliedContext.isClosed()) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "enhancement context is closed", null);
            }
            if (suppliedContext.diagnostics() != suppliedDiagnostics) {
                throw failure(
                        EnhancementFailure.Category.CONFIGURATION,
                        null,
                        "configure",
                        "enhancement context and diagnostics disagree",
                        null);
            }
        }

        private void openContext(ClassLoader loader) {
            if (suppliedContext != null) {
                if (suppliedContext.isClosed()) {
                    throw failure(EnhancementFailure.Category.LIFECYCLE, null, "configure", "enhancement context is closed", null);
                }
                if (suppliedContext.targetLoader() != loader) {
                    throw failure(
                            EnhancementFailure.Category.CONFIGURATION,
                            null,
                            "configure",
                            "enhancement context loader does not match the configuration loader",
                            null);
                }
                if (suppliedDiagnostics != null && suppliedContext.diagnostics() != suppliedDiagnostics) {
                    throw failure(
                            EnhancementFailure.Category.CONFIGURATION,
                            null,
                            "configure",
                            "enhancement context and diagnostics disagree",
                            null);
                }
                context = suppliedContext;
                ownsContext = false;
            } else if (suppliedDiagnostics != null) {
                context = EnhancementContext.open(loader, suppliedDiagnostics);
                ownsContext = true;
            } else {
                context = EnhancementContext.open(loader);
                ownsContext = true;
            }
            if (ClassDefiner.ANCHOR_SIMPLE_NAME.equals(classLoader.getSimpleName())) {
                context.classDefiner().registerAnchor(classLoader);
            }
            for (int i = 0; i < anchors.size(); i++) {
                context.classDefiner().registerAnchor(anchors.get(i));
            }
        }

        private void connect(ClassLoader loader) {
            StartupPhaseTrace.Frame frame = StartupPhaseTrace.open(context, "mongo.connect");
            try {
                connectClient(loader);
            } finally {
                frame.close();
            }
        }

        /**
         * Connects the driver client and validates it with ping.
         * <p>
         * {@code {mode}.datasources.mongodb.uri} takes precedence over the
         * discrete host/port/username/password/authenticationDatabase settings:
         * when it is set, credentials and hosts come from the connection string
         * (replica sets and TLS go through URI options). A database path in the
         * URI wins over {@code {mode}.datasources.mongodb.database}; when the
         * URI has no database path the discrete database setting applies. The
         * URI value itself is never put into an exception or log line.
         */
        private void connectClient(ClassLoader loader) {
            String prefix = mode + ".datasources.mongodb.";
            String uri = settings.get(prefix + "uri");
            String database = settings.get(prefix + "database", "csdn_data_center");
            String replicaSet = settings.get(prefix + "replicaSet", "");
            MongoClientOptions.Builder options = MongoClientOptions.builder()
                    .serverSelectionTimeout(8000)
                    .connectTimeout(8000)
                    .socketTimeout(20000);
            if (replicaSet != null && replicaSet.length() > 0) {
                options.requiredReplicaSetName(replicaSet);
            }
            MongoClient client;
            if (uri != null && uri.trim().length() > 0) {
                MongoClientURI mongoURI;
                try {
                    mongoURI = new MongoClientURI(uri.trim(), options);
                } catch (Throwable thrown) {
                    // The connection string can carry credentials; the parser
                    // message may embed it, so the cause is dropped on purpose.
                    throw failure(EnhancementFailure.Category.CONFIGURATION, null, "connect", "mongodb uri is invalid", null);
                }
                try {
                    client = new MongoClient(mongoURI);
                } catch (Throwable thrown) {
                    throw connectFailure(thrown);
                }
                String uriDatabase = mongoURI.getDatabase();
                if (uriDatabase != null && uriDatabase.length() > 0) {
                    database = uriDatabase;
                }
            } else {
                String host = settings.get(prefix + "host", "127.0.0.1");
                int port = settings.getAsInt(prefix + "port", Integer.valueOf(27017)).intValue();
                String username = settings.get(prefix + "username", "");
                String password = settings.get(prefix + "password");
                String authDatabase = settings.get(prefix + "authenticationDatabase", settings.get(prefix + "authdb", "admin"));
                if (username != null && username.length() > 0 && password == null) {
                    throw failure(EnhancementFailure.Category.CONFIGURATION, null, "connect", "mongodb password is required when username is set", null);
                }
                MongoClientOptions built = options.build();
                ServerAddress address = new ServerAddress(host, port);
                try {
                    if (username == null || username.length() == 0) {
                        client = new MongoClient(address, built);
                    } else {
                        MongoCredential credential = MongoCredential.createScramSha256Credential(
                                username,
                                authDatabase,
                                password.toCharArray());
                        client = new MongoClient(address, credential, built);
                    }
                } catch (Throwable thrown) {
                    throw connectFailure(thrown);
                }
            }
            mongoMongo = new MongoMongo(client, database, this);
            final CSDNMongoConfiguration owner = this;
            clientCloseable = new Closeable() {
                @Override
                public void close() {
                    // Context.close marks itself closed before running closers.
                    // Do not call context.getAttribute from here.
                    End end;
                    boolean markClosed;
                    synchronized (owner) {
                        markClosed = owner.markClosedFromContext();
                        end = owner.endLocked(markClosed);
                    }
                    owner.complete(end, markClosed);
                }
            };
            context.setAttribute(CONTEXT_ATTRIBUTE, mongoMongo);
            context.track(clientCloseable);
            try {
                CommandResult ping = client.getDB(database).command(new BasicDBObject("ping", 1));
                if (!ping.ok()) {
                    String driverMessage = ping.getErrorMessage();
                    throw failure(
                            EnhancementFailure.Category.CONFIGURATION,
                            null,
                            "connect",
                            "mongo ping was not ok",
                            new IllegalStateException(driverMessage == null ? "not ok" : driverMessage));
                }
            } catch (EnhancementFailure failure) {
                throw failure;
            } catch (Throwable thrown) {
                throw connectFailure(thrown);
            }
            if (loader == null) {
                throw failure(EnhancementFailure.Category.CONFIGURATION, null, "connect", "target class loader is required", null);
            }
        }

        private void loadDocuments(ClassLoader loader, String documentPackage) throws IOException, NotFoundException {
            final ClassPool pool = context.classPool();
            final List<CtClass> documents = new ArrayList<CtClass>();
            ScanService scanService = new DefaultScanService();
            scanService.setLoader(classLoader);
            StartupPhaseTrace.Frame scan = StartupPhaseTrace.open(context, "scan.mongo");
            try {
                scanService.scanArchives(documentPackage, new ScanService.LoadClassEnhanceCallBack() {
                    @Override
                    public Class loaded(DataInputStream classFile) {
                        CtClass type = readType(pool, classFile);
                        if (!include(type)) {
                            type.detach();
                            return null;
                        }
                        context.track(type);
                        documents.add(type);
                        return null;
                    }
                });
            } catch (EnhancementFailure failure) {
                throw failure;
            } catch (IOException e) {
                throw failure(EnhancementFailure.Category.SCAN, null, "scan", "document scan failed", e);
            } finally {
                scan.close();
            }
            if (documents.isEmpty()) {
                throw failure(
                        EnhancementFailure.Category.SCAN,
                        null,
                        "scan",
                        "no Document subclass found",
                        null);
            }
            sortParentsFirst(documents);
            SchemaNote schemaNote = SchemaNote.notNoted();
            try {
                schemaNote = noteModelSchema(documents);
                EnhancementPlan plan = compilePlan();
                for (int i = 0; i < documents.size(); i++) {
                    CtClass type = documents.get(i);
                    try {
                        plan.apply(type, context);
                    } catch (EnhancementFailure failure) {
                        throw failure;
                    } catch (RuntimeException e) {
                        throw failure(EnhancementFailure.Category.ENHANCEMENT, type.getName(), "enhance", "plan apply failed", e);
                    }
                }
                List<Class<?>> defined = new ArrayList<Class<?>>();
                for (int i = 0; i < documents.size(); i++) {
                    CtClass type = documents.get(i);
                    Class<?> loaded = context.define(type);
                    markDefined(loader);
                    defined.add(loaded);
                    logger.info("defined " + loaded.getName());
                }
                for (int i = 0; i < defined.size(); i++) {
                    initialize(defined.get(i), loader);
                }
            } finally {
                restoreModelSchema(schemaNote);
            }
        }

        /**
         * Snapshots the model-schema digest onto events recorded while Mongo
         * is enhancing. An application revision already stored on the
         * diagnostics object is kept. {@code mongo-1} is the model-format
         * token and is written only when the caller supplied no revision.
         * An earlier token is restored afterwards, together with the previous
         * digest, so a later event does not keep this digest. Disabled
         * diagnostics return before any field walk or hash.
         */
        private SchemaNote noteModelSchema(List<CtClass> documents) {
            EnhancementDiagnostics diagnostics = context.diagnostics();
            if (diagnostics == null || !diagnostics.enabled()) {
                return SchemaNote.notNoted();
            }
            String previousVersion = diagnostics.configVersion();
            String previousDigest = diagnostics.schemaDigest();
            String digest = MongoModelSchema.digest(documents);
            modelSchemaDigestComputations++;
            String revision = previousVersion != null ? previousVersion : MongoModelSchema.VERSION;
            diagnostics.noteSafeMetadata(revision, digest);
            return new SchemaNote(true, previousVersion, previousDigest);
        }

        private void restoreModelSchema(SchemaNote note) {
            if (note == null || !note.noted || note.previousVersion == null) {
                return;
            }
            if (context == null || context.isClosed()) {
                return;
            }
            EnhancementDiagnostics diagnostics = context.diagnostics();
            if (diagnostics == null || !diagnostics.enabled()) {
                return;
            }
            diagnostics.noteSafeMetadata(note.previousVersion, note.previousDigest);
        }

        private static CtClass readType(ClassPool pool, DataInputStream classFile) {
            try {
                return pool.makeClass(classFile);
            } catch (IOException e) {
                throw failure(EnhancementFailure.Category.SCAN, null, "scan", "cannot read class file", e);
            } catch (RuntimeException e) {
                throw failure(EnhancementFailure.Category.SCAN, null, "scan", "cannot read class file", e);
            }
        }

        private boolean include(CtClass type) {
            try {
                return new MongoDocumentRule().matches(type, context);
            } catch (EnhancementFailure failure) {
                throw failure;
            }
        }

        private static void sortParentsFirst(List<CtClass> documents) throws NotFoundException {
            final Map<String, Integer> depth = new HashMap<String, Integer>();
            for (int i = 0; i < documents.size(); i++) {
                CtClass type = documents.get(i);
                depth.put(type.getName(), Integer.valueOf(depthOf(type)));
            }
            Collections.sort(documents, new Comparator<CtClass>() {
                @Override
                public int compare(CtClass left, CtClass right) {
                    int delta = depth.get(left.getName()).intValue() - depth.get(right.getName()).intValue();
                    if (delta != 0) {
                        return delta;
                    }
                    return left.getName().compareTo(right.getName());
                }
            });
        }

        private static int depthOf(CtClass type) throws NotFoundException {
            int depth = 0;
            CtClass current = type;
            while (current != null
                    && !"net.csdn.mongo.Document".equals(current.getName())
                    && !"java.lang.Object".equals(current.getName())) {
                depth++;
                current = current.getSuperclass();
            }
            return depth;
        }

        private EnhancementPlan compilePlan() {
            EnhancementRules rules = new EnhancementRules();
            rules.register(new MongoDocumentRule());
            for (int i = 0; i < extraRules.size(); i++) {
                rules.register(extraRules.get(i));
            }
            return rules.compile();
        }

        private static void initialize(Class<?> type, ClassLoader loader) {
            try {
                Class.forName(type.getName(), true, loader);
            } catch (ExceptionInInitializerError e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof EnhancementFailure) {
                    throw (EnhancementFailure) cause;
                }
                throw failure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        type.getName(),
                        "initialize",
                        "static initialization failed",
                        cause);
            } catch (ClassNotFoundException e) {
                throw failure(EnhancementFailure.Category.DEFINITION, type.getName(), "initialize", "defined class is not visible", e);
            }
        }

        private void loadValidators() {
            String[] names = new String[]{
                    "net.csdn.mongo.validate.impl.Format",
                    "net.csdn.mongo.validate.impl.Numericality",
                    "net.csdn.mongo.validate.impl.Presence",
                    "net.csdn.mongo.validate.impl.Uniqueness",
                    "net.csdn.mongo.validate.impl.Length",
                    "net.csdn.mongo.validate.impl.Associated"
            };
            synchronized (Document.validateParses) {
                Set<String> present = new HashSet<String>();
                for (int i = 0; i < Document.validateParses.size(); i++) {
                    present.add(Document.validateParses.get(i).getClass().getName());
                }
                for (int i = 0; i < names.length; i++) {
                    if (present.contains(names[i])) {
                        continue;
                    }
                    try {
                        Document.validateParses.add(Class.forName(names[i]).newInstance());
                    } catch (Exception e) {
                        throw failure(EnhancementFailure.Category.DEPENDENCY, names[i], "validator", "cannot load validator", e);
                    }
                }
            }
        }

        /**
         * One end path for {@link #close()} and for the closer tracked on the
         * context. The client is unpublished and closed at most once.
         * {@code markClosed} moves the state to {@link State#CLOSED} only when
         * this configuration does not still hold an open context it owns.
         * A rejected owned-context close keeps that context so a later call can
         * finish it. A failure cleanup passes {@code false} so the state stays
         * {@link State#FAILED}. Caller holds the configuration monitor.
         */
        private End endLocked(boolean markClosed) {
            if (resourcesReleased.get() && !ownedStillOpen()) {
                if (markClosed) {
                    state = State.CLOSED;
                }
                return new End(null, null);
            }
            Throwable clientFailure = null;
            if (resourcesReleased.compareAndSet(false, true)) {
                MongoMongo bound = mongoMongo;
                unpublish(bound);
                clientCloseable = null;
                if (bound != null) {
                    clientFailure = bound.closeClient();
                }
            }
            EnhancementContext owned = ownedStillOpen() ? context : null;
            if (markClosed && owned == null) {
                state = State.CLOSED;
            }
            return new End(clientFailure, owned);
        }

        private boolean markClosedFromContext() {
            return state != State.FAILED && state != State.DISABLED && state != State.NEW;
        }

        private boolean ownedStillOpen() {
            return ownsContext && context != null && !context.isClosed();
        }

        private void finish(boolean markClosed) {
            End end;
            synchronized (this) {
                end = endLocked(markClosed);
            }
            complete(end, markClosed);
        }

        /**
         * Closes the owned context after the configuration monitor is released.
         * A client-close failure does not skip this. The first failure is
         * thrown, and the other is {@link Throwable#addSuppressed suppressed}.
         * An owned context that is still open stays reachable for a later close.
         */
        private void complete(End end, boolean markClosed) {
            Throwable contextFailure = null;
            if (end.owned != null) {
                try {
                    end.owned.close();
                } catch (Throwable thrown) {
                    contextFailure = thrown;
                }
            }
            synchronized (this) {
                if (markClosed && !ownedStillOpen()) {
                    state = State.CLOSED;
                }
            }
            throwCombined(end.clientFailure, contextFailure);
        }

        private void releaseQuietly() {
            finish(false);
        }

        private static void throwCombined(Throwable primary, Throwable secondary) {
            if (secondary != null && secondary != primary) {
                if (primary == null) {
                    primary = secondary;
                } else {
                    primary.addSuppressed(secondary);
                }
            }
            if (primary == null) {
                return;
            }
            if (primary instanceof Error) {
                throw (Error) primary;
            }
            if (primary instanceof RuntimeException) {
                throw (RuntimeException) primary;
            }
            throw failure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    "close",
                    primary.getMessage() == null ? primary.getClass().getName() : primary.getMessage(),
                    primary);
        }

        private static final class SchemaNote {
            private final boolean noted;
            private final String previousVersion;
            private final String previousDigest;

            private SchemaNote(boolean noted, String previousVersion, String previousDigest) {
                this.noted = noted;
                this.previousVersion = previousVersion;
                this.previousDigest = previousDigest;
            }

            private static SchemaNote notNoted() {
                return new SchemaNote(false, null, null);
            }
        }

        private static final class End {
            private final Throwable clientFailure;
            private final EnhancementContext owned;

            private End(Throwable clientFailure, EnhancementContext owned) {
                this.clientFailure = clientFailure;
                this.owned = owned;
            }
        }
    }

    /**
     * Identity-keyed weak set. {@link java.util.WeakHashMap} compares keys with
     * {@code equals}, so two live loaders that compare equal would block each
     * other. Caller holds {@link #OPEN_LOCK}.
     */
    private static final class WeakIdentityLoaders {
        private final ReferenceQueue<ClassLoader> queue = new ReferenceQueue<ClassLoader>();
        private final Map<Integer, List<LoaderKey>> buckets = new HashMap<Integer, List<LoaderKey>>();

        private static final class LoaderKey extends WeakReference<ClassLoader> {
            private final int identity;

            private LoaderKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
                super(loader, queue);
                this.identity = System.identityHashCode(loader);
            }
        }

        boolean contains(ClassLoader loader) {
            if (loader == null) {
                return false;
            }
            expunge();
            List<LoaderKey> list = buckets.get(Integer.valueOf(System.identityHashCode(loader)));
            if (list == null) {
                return false;
            }
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).get() == loader) {
                    return true;
                }
            }
            return false;
        }

        void add(ClassLoader loader) {
            if (loader == null || contains(loader)) {
                return;
            }
            int identity = System.identityHashCode(loader);
            Integer key = Integer.valueOf(identity);
            List<LoaderKey> list = buckets.get(key);
            if (list == null) {
                list = new ArrayList<LoaderKey>();
                buckets.put(key, list);
            }
            list.add(new LoaderKey(loader, queue));
        }

        int size() {
            expunge();
            int count = 0;
            for (List<LoaderKey> list : buckets.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).get() != null) {
                        count++;
                    }
                }
            }
            return count;
        }

        private void expunge() {
            Reference<?> polled = queue.poll();
            while (polled != null) {
                LoaderKey key = (LoaderKey) polled;
                List<LoaderKey> list = buckets.get(Integer.valueOf(key.identity));
                if (list != null) {
                    list.remove(key);
                    if (list.isEmpty()) {
                        buckets.remove(Integer.valueOf(key.identity));
                    }
                }
                polled = queue.poll();
            }
        }
    }
}
