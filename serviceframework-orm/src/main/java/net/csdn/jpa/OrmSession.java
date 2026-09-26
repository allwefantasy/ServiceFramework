package net.csdn.jpa;

import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.JdbcEngine;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.type.DBInfo;
import net.csdn.modules.persist.mysql.DataSourceManager;
import net.csdn.modules.persist.mysql.MysqlClient;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ORM state owned by one {@link EnhancementContext}. The closer keeps its own
 * resource references and does not read context attributes during close.
 */
public final class OrmSession {

    public static final String ATTRIBUTE = "net.csdn.jpa.OrmSession";

    private final EnhancementContext context;
    private final ModelRegistry registry = new ModelRegistry();
    private final Map<String, ModelClass> modelClasses = new LinkedHashMap<String, ModelClass>();
    private final List<ModelClass> roots = new ArrayList<ModelClass>();
    private final Set<String> mappedNames = new LinkedHashSet<String>();
    private final Closeable closer = new ResourceCloser(this);
    private JPA.CSDNORMConfiguration configuration;
    private DBInfo dbInfo;
    private JPAConfig jpaConfig;
    private final List<Class<?>> managedTypes = new ArrayList<Class<?>>();
    private String fingerprint;
    private boolean configured;
    private boolean definedModels;
    private boolean entityMappingDone;
    private MysqlClient sqlClient;
    private Object quillState;
    private final List<Closeable> closeables = new ArrayList<Closeable>();
    private boolean resourcesClosed;

    private OrmSession(EnhancementContext context) {
        this.context = context;
    }

    public static OrmSession attach(EnhancementContext context) {
        if (context == null || context.isClosed()) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "session",
                    "enhancement context is required",
                    null);
        }
        Object existing = context.getAttribute(ATTRIBUTE);
        if (existing instanceof OrmSession) {
            return (OrmSession) existing;
        }
        OrmSession session = new OrmSession(context);
        context.setAttribute(ATTRIBUTE, session);
        context.track(session.closer);
        return session;
    }

    public static OrmSession current() {
        EnhancementContext active = EnhancementContext.currentOrNull();
        if (active != null) {
            if (active.isClosed()) {
                throw sessionFailure("the active enhancement context is closed");
            }
            OrmSession session = from(active);
            if (session == null) {
                throw sessionFailure("the active enhancement context has no ORM session");
            }
            return session;
        }
        OrmSession session = from(JPA.defaultContext());
        if (session != null) {
            return session;
        }
        throw sessionFailure("JPA is not configured");
    }

    public static OrmSession currentOrNull() {
        EnhancementContext active = EnhancementContext.currentOrNull();
        if (active != null) {
            return from(active);
        }
        return from(JPA.defaultContext());
    }

    private static EnhancementFailure sessionFailure(String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                null,
                "session",
                detail,
                null);
    }

    private static OrmSession from(EnhancementContext context) {
        if (context == null || context.isClosed()) {
            return null;
        }
        Object value = context.getAttribute(ATTRIBUTE);
        if (value instanceof OrmSession) {
            return (OrmSession) value;
        }
        return null;
    }

    public EnhancementContext context() {
        return context;
    }

    public ModelRegistry registry() {
        return registry;
    }

    public JPA.CSDNORMConfiguration configuration() {
        if (configuration == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "session",
                    "ORM configuration is missing",
                    null);
        }
        return configuration;
    }

    public void bindConfiguration(JPA.CSDNORMConfiguration configuration) {
        if (definedModels) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    null,
                    null,
                    "configure",
                    "models are already defined in this loader; create a new application ClassLoader",
                    null);
        }
        this.configuration = configuration;
        this.configured = false;
        this.entityMappingDone = false;
        this.roots.clear();
        this.modelClasses.clear();
        this.mappedNames.clear();
        this.managedTypes.clear();
        this.registry.clear();
    }

    public boolean hasConfiguration() {
        return configuration != null;
    }

    public DBInfo dbInfo() {
        return dbInfo;
    }

    public void setDbInfo(DBInfo dbInfo) {
        this.dbInfo = dbInfo;
    }

    public JPAConfig jpaConfig() {
        return jpaConfig;
    }

    public void setJpaConfig(JPAConfig jpaConfig) {
        synchronized (this) {
            ensureOpen();
            if (this.jpaConfig == jpaConfig) {
                return;
            }
            this.jpaConfig = jpaConfig;
            if (jpaConfig != null) {
                closeables.add(new JpaConfigCloser(jpaConfig));
            }
        }
    }

    /**
     * Selected JDBC client for this application. A disabled or unconfigured
     * context does not open a pool. The historical {@code MysqlClient} type is
     * engine-neutral JDBC plumbing; engine-specific APIs below gate by name.
     */
    public MysqlClient sqlClient() {
        synchronized (this) {
            ensureOpen();
            if (sqlClient != null) {
                return sqlClient;
            }
        }
        if (!hasConfiguration() || primaryDisabled()) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "datasource",
                    "primary datasource is not available in this context",
                    null);
        }
        Settings settings = configuration.getSettings();
        DataSourceManager manager = DataSourceManager.forMode(settings, configuration.getMode());
        try {
            MysqlClient created = new MysqlClient(manager, settings);
            synchronized (this) {
                if (resourcesClosed) {
                    manager.close();
                    throw sessionFailure("ORM context is closed");
                }
                if (sqlClient != null) {
                    manager.close();
                    return sqlClient;
                }
                sqlClient = created;
                closeables.add(manager);
                return created;
            }
        } catch (RuntimeException e) {
            try {
                manager.close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * MySQL-specific legacy entry point. It does not silently run against a
     * PostgreSQL primary.
     */
    public MysqlClient mysqlClient() {
        requirePrimaryEngine(JdbcEngine.MYSQL);
        return sqlClient();
    }

    /**
     * PostgreSQL entry point for the selected primary datasource.
     */
    public MysqlClient postgresClient() {
        requirePrimaryEngine(JdbcEngine.POSTGRES);
        return sqlClient();
    }

    /**
     * This session has a configuration whose selected JDBC pools may be opened.
     * A disabled or missing configuration is false and must not borrow another session.
     */
    public boolean datasourceAvailable() {
        if (resourcesClosed || !configured || configuration == null) {
            return false;
        }
        return !primaryDisabled();
    }

    /**
     * This session has an enabled MySQL primary.
     */
    public boolean mysqlAvailable() {
        return datasourceAvailable() && JdbcEngine.MYSQL.equals(primaryEngine());
    }

    /**
     * This session has an enabled PostgreSQL primary.
     */
    public boolean postgresAvailable() {
        return datasourceAvailable() && JdbcEngine.POSTGRES.equals(primaryEngine());
    }

    public Object quillState() {
        return quillState;
    }

    public void bindQuill(Object state, Closeable closer) {
        synchronized (this) {
            ensureOpen();
            if (quillState != null) {
                return;
            }
            quillState = state;
            if (closer != null) {
                closeables.add(closer);
            }
        }
    }

    private boolean primaryDisabled() {
        if (configuration == null) {
            return true;
        }
        return JdbcEngine.primaryDisabled(configuration.getSettings(), configuration.getMode());
    }

    private String primaryEngine() {
        if (configuration == null) {
            return null;
        }
        return JdbcEngine.primary(configuration.getSettings(), configuration.getMode()).engine();
    }

    private void requirePrimaryEngine(String engine) {
        String actual = primaryEngine();
        if (!JdbcEngine.normalize(engine).equals(actual)) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "datasource",
                    JdbcEngine.normalize(engine) + " datasource API requires datasources.primary=" + JdbcEngine.normalize(engine)
                            + "; actual primary is " + String.valueOf(actual),
                    null);
        }
    }

    private void ensureOpen() {
        if (resourcesClosed) {
            throw sessionFailure("ORM context is closed");
        }
    }

    public List<Class<?>> managedTypes() {
        return Collections.unmodifiableList(managedTypes);
    }

    public void addManagedType(Class<?> type) {
        if (type != null && !managedTypes.contains(type)) {
            managedTypes.add(type);
        }
    }

    public String fingerprint() {
        return fingerprint;
    }

    public void markConfigured(String fingerprint) {
        this.fingerprint = fingerprint;
        this.configured = true;
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean hasDefinedModels() {
        return definedModels;
    }

    public void markDefinedModels() {
        this.definedModels = true;
    }

    public boolean entityMappingDone() {
        return entityMappingDone;
    }

    public void markEntityMappingDone() {
        this.entityMappingDone = true;
    }

    public boolean knowsMappedType(String binaryName) {
        return mappedNames.contains(binaryName);
    }

    public void replaceModelTree(List<ModelClass> nextRoots, List<ModelClass> all) {
        roots.clear();
        modelClasses.clear();
        mappedNames.clear();
        if (nextRoots != null) {
            roots.addAll(nextRoots);
        }
        if (all == null) {
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            ModelClass modelClass = all.get(i);
            modelClasses.put(modelClass.originClass.getName(), modelClass);
            mappedNames.add(modelClass.originClass.getName());
        }
    }

    public List<ModelClass> roots() {
        return Collections.unmodifiableList(roots);
    }

    public ModelClass modelClass(String binaryName) {
        return modelClasses.get(binaryName);
    }

    public void closeResources() {
        List<Closeable> snapshot;
        synchronized (this) {
            if (resourcesClosed) {
                return;
            }
            resourcesClosed = true;
            configured = false;
            snapshot = new ArrayList<Closeable>(closeables);
            closeables.clear();
            jpaConfig = null;
            sqlClient = null;
            quillState = null;
        }
        List<Throwable> failures = new ArrayList<Throwable>();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            try {
                snapshot.get(i).close();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        if (failures.isEmpty()) {
            return;
        }
        Throwable primary = failures.get(0);
        for (int i = 1; i < failures.size(); i++) {
            primary.addSuppressed(failures.get(i));
        }
        if (primary instanceof Error) {
            throw (Error) primary;
        }
        if (primary instanceof RuntimeException) {
            throw (RuntimeException) primary;
        }
        throw new EnhancementFailure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                null,
                "close",
                "ORM resources were not closed",
                primary instanceof Exception ? (Exception) primary : new RuntimeException(primary));
    }

    /**
     * Closes the JPA resources captured by this session. Invoked by the context
     * while it is closing; it must not call back into the context.
     */
    private static final class ResourceCloser implements Closeable {
        private final OrmSession session;

        private ResourceCloser(OrmSession session) {
            this.session = session;
        }

        @Override
        public void close() throws IOException {
            session.closeResources();
        }
    }

    private static final class JpaConfigCloser implements Closeable {
        private final JPAConfig config;

        private JpaConfigCloser(JPAConfig config) {
            this.config = config;
        }

        @Override
        public void close() {
            config.shutdown();
        }
    }
}
