package net.csdn.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.extension.ExtensionSession;
import net.csdn.bootstrap.loader.impl.ControllerLoader;
import net.csdn.bootstrap.loader.impl.LoggerLoader;
import net.csdn.bootstrap.loader.impl.ModuelLoader;
import net.csdn.bootstrap.loader.impl.ServiceLoader;
import net.csdn.bootstrap.loader.impl.TemplateLoader;
import net.csdn.bootstrap.loader.impl.ThriftLoader;
import net.csdn.bootstrap.loader.impl.UtilLoader;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.StartupPhaseTrace;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.modules.dubbo.DubboServer;
import net.csdn.modules.http.HttpServer;
import net.csdn.modules.http.support.ControllerFilterPlan;
import net.csdn.modules.threadpool.ThreadPoolService;
import net.csdn.modules.thrift.ThriftServer;
import net.csdn.modules.transport.DefaultHttpTransportService;
import net.csdn.modules.transport.HttpTransportService;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owner of one application's enhancement context, Guice modules, injector,
 * filter metadata and close actions.
 * <p>
 * The active instance is the one on the calling thread's
 * {@link EnhancementContext} scope. A scope does not cross threads.
 * {@link #capture(Runnable)} binds this application on another thread.
 * Static fields on {@link ServiceFramwork} alias only the default application
 * and are not updated for a second loader.
 * <p>
 * {@link #start(Settings)} is idempotent once running. {@link #close()} is
 * idempotent after it finishes. A close while any thread still holds this
 * application's enhancement scope is refused: state, the HTTP server and the
 * enhancement context stay as they were, so a later close can release them.
 * The call does not wait for that scope. When no scope is open, close reserves
 * shutdown before it stops servers or releases resources, and both
 * {@link #activate()} and {@link EnhancementContext#activate()} fail from that
 * moment. The reservation lock is not held while server threads are joined.
 * A concurrent close waits for that one cleanup instead of starting another.
 * Cleanup failures after a close has been accepted are aggregated; later steps
 * still run.
 * <p>
 * A loader that has defined classes is remembered by identity, weakly. The
 * same live loader cannot define again. A different loader is not rejected
 * merely because its {@code equals} matches. Dropping the application and the
 * loader lets the loader be collected. {@code RUNNING} and {@code STARTING}
 * do not keep a loader after failure or a finished close. Closing the default
 * application clears {@link #defaultContext}.
 */
public final class ApplicationContext implements AutoCloseable {

    public static final String ATTRIBUTE = "net.csdn.ApplicationContext";

    private static final Object LOCK = new Object();
    private static final Map<ClassLoader, ApplicationContext> RUNNING =
            new IdentityHashMap<ClassLoader, ApplicationContext>();
    private static final Map<ClassLoader, ApplicationContext> STARTING =
            new IdentityHashMap<ClassLoader, ApplicationContext>();
    private static final WeakIdentityLoaders DEFINED = new WeakIdentityLoaders();

    private static volatile ApplicationContext defaultContext;

    private final Object lifecycle = new Object();
    private final boolean legacyDefault;
    private final ClassLoader targetLoader;
    private final List<Module> registeredModules;
    private final List<Module> serviceModules;
    private final List<Module> allModules;
    private final List<Class> startWithSystem;
    private final List<Module> controllerModules = new ArrayList<Module>();
    private final List<EnhancementRule> controllerRules = new ArrayList<EnhancementRule>();
    private final List<FrameworkExtension> programmatic = new ArrayList<FrameworkExtension>();
    private final List<Class<?>> controllers = new ArrayList<Class<?>>();
    private final ExtensionSession extensions = new ExtensionSession();

    private Class<?> marker;
    private ScanService scanService;
    private EnhancementContext enhancement;
    private Settings settings;
    private ServiceFramwork.Mode mode = ServiceFramwork.Mode.development;
    private Injector injector;
    private ControllerFilterPlan filters = ControllerFilterPlan.compile(Collections.<Class<?>>emptyList());
    private HttpServer httpServer;
    private ThriftServer thriftServer;
    private DubboServer dubboServer;
    private boolean httpDisabled;
    private boolean thriftDisabled = true;
    private boolean dubboDisabled = true;
    private boolean noThreadJoin;
    private boolean httpStarted;
    private boolean thriftStarted;
    private int httpPort = -1;
    private boolean classesDefined;
    private int moduleBaseline;
    private State state = State.NEW;
    private volatile boolean serversStopped;
    private volatile boolean detached;
    private boolean closeRunning;
    private Thread closingThread;

    private ApplicationContext(Class<?> marker, boolean legacyDefault, EnhancementDiagnostics diagnostics) {
        if (marker == null || marker.getClassLoader() == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, "target ClassLoader is required");
        }
        this.marker = marker;
        this.legacyDefault = legacyDefault;
        this.targetLoader = marker.getClassLoader();
        if (legacyDefault) {
            this.registeredModules = ServiceFramwork.modules;
            this.serviceModules = ServiceFramwork.serviceModules;
            this.allModules = ServiceFramwork.AllModules;
            this.startWithSystem = ServiceFramwork.startWithSystem;
            this.scanService = ServiceFramwork.scanService;
            this.scanService.setLoader(marker);
        } else {
            this.registeredModules = new ArrayList<Module>();
            this.serviceModules = new ArrayList<Module>();
            this.allModules = new ArrayList<Module>();
            this.startWithSystem = new ArrayList<Class>();
            this.scanService = new DefaultScanService();
            this.scanService.setLoader(marker);
        }
        this.enhancement = diagnostics == null
                ? EnhancementContext.open(targetLoader)
                : EnhancementContext.open(targetLoader, diagnostics);
        this.enhancement.setAttribute(ATTRIBUTE, this);
    }

    public static ApplicationContext open(Class<?> marker) {
        return open(marker, false, null);
    }

    /**
     * Creates an application that records enhancement into {@code diagnostics}.
     * Null or a disabled instance keeps the default: no hashes, no bytecode
     * reads, no report directory. Diagnostics are kept only when this call
     * creates the context; an already running loader returns that context.
     */
    public static ApplicationContext open(Class<?> marker, EnhancementDiagnostics diagnostics) {
        return open(marker, false, diagnostics);
    }

    static ApplicationContext open(Class<?> marker, boolean legacyDefault) {
        return open(marker, legacyDefault, null);
    }

    static ApplicationContext open(Class<?> marker, boolean legacyDefault, EnhancementDiagnostics diagnostics) {
        ClassLoader loader = marker == null ? null : marker.getClassLoader();
        synchronized (LOCK) {
            ApplicationContext running = RUNNING.get(loader);
            if (running != null) {
                return running;
            }
            if (STARTING.containsKey(loader) || DEFINED.contains(loader)) {
                throw failure(EnhancementFailure.Category.CONFLICT,
                        "classes were already defined in this loader; hot reload is not supported");
            }
        }
        return new ApplicationContext(marker, legacyDefault, diagnostics);
    }

    public static ApplicationContext currentOrNull() {
        EnhancementContext enhancement = EnhancementContext.currentOrNull();
        if (enhancement == null || enhancement.isClosed()) {
            return null;
        }
        Object value = enhancement.getAttribute(ATTRIBUTE);
        if (value instanceof ApplicationContext) {
            return (ApplicationContext) value;
        }
        return null;
    }

    public static boolean foreignScope() {
        EnhancementContext enhancement = EnhancementContext.currentOrNull();
        if (enhancement == null || enhancement.isClosed()) {
            return false;
        }
        return !(enhancement.getAttribute(ATTRIBUTE) instanceof ApplicationContext);
    }

    public static ApplicationContext require() {
        if (foreignScope()) {
            throw failure(EnhancementFailure.Category.LIFECYCLE,
                    "active enhancement context is not an application context");
        }
        ApplicationContext current = currentOrNull();
        if (current == null) {
            throw failure(EnhancementFailure.Category.LIFECYCLE,
                    "no application context is active on this thread");
        }
        return current;
    }

    public static ApplicationContext defaultContext() {
        return defaultContext;
    }

    static ApplicationContext bootstrapDefault(Settings settings) throws Exception {
        Class<?> marker = defaultMarker();
        ApplicationContext created;
        synchronized (LOCK) {
            if (defaultContext != null && defaultContext.isRunning()) {
                return defaultContext;
            }
            created = open(marker, true);
            defaultContext = created;
        }
        try {
            created.start(settings);
            return created;
        } catch (Throwable thrown) {
            synchronized (LOCK) {
                if (defaultContext == created && !created.isRunning()) {
                    defaultContext = null;
                }
            }
            rethrow(thrown);
            return created;
        }
    }

    public void start(Settings settings) throws Exception {
        if (settings == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, "settings are required");
        }
        synchronized (lifecycle) {
            if (state == State.RUNNING) {
                return;
            }
            if (state != State.NEW) {
                throw failure(EnhancementFailure.Category.LIFECYCLE,
                        "application context is closed; create a new one with a new ClassLoader");
            }
            synchronized (LOCK) {
                ApplicationContext starting = STARTING.get(targetLoader);
                ApplicationContext running = RUNNING.get(targetLoader);
                if ((running != null && running != this) || (starting != null && starting != this)
                        || DEFINED.contains(targetLoader)) {
                    throw failure(EnhancementFailure.Category.CONFLICT,
                            "classes were already defined in this loader; hot reload is not supported");
                }
                STARTING.put(targetLoader, this);
            }
            state = State.STARTING;
        }
        Throwable failure = null;
        EnhancementContext.Scope scope = null;
        try {
            scope = enhancement.activate();
            doStart(settings);
        } catch (Throwable thrown) {
            failure = thrown;
        } finally {
            if (scope != null) {
                try {
                    scope.close();
                } catch (Throwable thrown) {
                    if (failure == null) {
                        failure = thrown;
                    } else {
                        failure.addSuppressed(thrown);
                    }
                }
            }
        }
        if (failure != null) {
            try {
                finishClose();
            } catch (Throwable thrown) {
                failure.addSuppressed(thrown);
            } finally {
                synchronized (LOCK) {
                    if (STARTING.get(targetLoader) == this) {
                        STARTING.remove(targetLoader);
                    }
                    if (RUNNING.get(targetLoader) == this) {
                        RUNNING.remove(targetLoader);
                    }
                    if (defaultContext == this) {
                        defaultContext = null;
                    }
                }
                synchronized (lifecycle) {
                    state = State.CLOSED;
                    lifecycle.notifyAll();
                }
            }
            trimModules();
            rethrow(failure);
        }
        synchronized (lifecycle) {
            state = State.RUNNING;
        }
        synchronized (LOCK) {
            STARTING.remove(targetLoader);
            RUNNING.put(targetLoader, this);
            if (legacyDefault) {
                ServiceFramwork.injector = injector;
                ServiceFramwork.mode = mode;
            }
        }
    }

    public EnhancementContext.Scope activate() {
        EnhancementContext current = enhancement;
        if (current == null || current.isClosed() || current.isShutdownAdmitted()) {
            throw failure(EnhancementFailure.Category.LIFECYCLE, "application context is closed");
        }
        return current.activate();
    }

    /**
     * Runs {@code task} on the calling thread of the returned runnable with this
     * application bound. The original thread's scope is not visible to the task.
     */
    public Runnable capture(final Runnable task) {
        if (task == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, "task is required");
        }
        final ApplicationContext owner = this;
        return new Runnable() {
            @Override
            public void run() {
                EnhancementContext.Scope scope = owner.activate();
                try {
                    task.run();
                } finally {
                    scope.close();
                }
            }
        };
    }

    public void addExtension(FrameworkExtension extension) {
        if (extension == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, "extension is required");
        }
        synchronized (lifecycle) {
            if (state != State.NEW) {
                throw failure(EnhancementFailure.Category.LIFECYCLE, "extensions are fixed once start begins");
            }
            programmatic.add(extension);
        }
    }

    public void addEnhancementRule(EnhancementRule rule) {
        if (rule == null) {
            throw failure(EnhancementFailure.Category.CONFIGURATION, "enhancement rule is required");
        }
        controllerRules.add(rule);
    }

    public Class<?> marker() {
        return marker;
    }

    public ClassLoader targetLoader() {
        return targetLoader;
    }

    public EnhancementContext enhancementContext() {
        if (enhancement == null || enhancement.isClosed()) {
            throw failure(EnhancementFailure.Category.LIFECYCLE, "enhancement context is closed");
        }
        return enhancement;
    }

    public ScanService scanService() {
        if (scanService == null) {
            throw failure(EnhancementFailure.Category.LIFECYCLE, "application context is closed");
        }
        return scanService;
    }

    public ServiceFramwork.Mode mode() {
        return mode;
    }

    public Settings settings() {
        return settings;
    }

    public Injector injector() {
        return injector;
    }

    public List<Module> registeredModules() {
        return registeredModules;
    }

    public List<Module> serviceModules() {
        return serviceModules;
    }

    public List<Module> allModules() {
        return allModules;
    }

    public List<Class> systemServices() {
        return startWithSystem;
    }

    public List<Module> controllerModules() {
        return controllerModules;
    }

    public List<EnhancementRule> controllerRules() {
        return controllerRules;
    }

    public void addController(Class<?> controller) {
        controllers.add(controller);
    }

    public void markClassesDefined() {
        classesDefined = true;
        DEFINED.add(targetLoader);
    }

    public ControllerFilterPlan filters() {
        return filters;
    }

    public void replaceFilters(ControllerFilterPlan plan) {
        this.filters = plan == null
                ? ControllerFilterPlan.compile(Collections.<Class<?>>emptyList())
                : plan;
    }

    public List<String> disabledExtensionClassNames() {
        return extensions.disabledNames();
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    public boolean isHttpStarted() {
        return httpStarted && httpServer != null && httpServer.isRunning();
    }

    public int httpPort() {
        return httpPort;
    }

    public boolean shouldJoin() {
        return !noThreadJoin && (httpStarted || thriftStarted || dubboServer != null);
    }

    @Override
    public void close() {
        EnhancementContext current = enhancement;
        if (current != null && !current.isClosed()) {
            EnhancementContext.ShutdownAdmission admission = current.admitShutdown();
            if (admission == EnhancementContext.ShutdownAdmission.REJECTED_ACTIVE) {
                throw failure(EnhancementFailure.Category.LIFECYCLE,
                        "close the active scopes on their owning threads before closing the application");
            }
            if (admission != EnhancementContext.ShutdownAdmission.RESERVED) {
                awaitForeignClose();
                return;
            }
        } else if (state == State.CLOSED && enhancementFinished()) {
            return;
        }
        finishClose();
    }

    private void awaitForeignClose() {
        synchronized (lifecycle) {
            while (closeRunning || state != State.CLOSED || !enhancementFinished()) {
                try {
                    lifecycle.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure(EnhancementFailure.Category.LIFECYCLE,
                            "application close was interrupted", interrupted);
                }
            }
        }
    }

    private void doStart(Settings settings) throws Exception {
        this.settings = settings;
        this.moduleBaseline = allModules.size();
        selectMode(settings);
        httpDisabled = Boolean.TRUE.equals(settings.getAsBoolean("http.disable", Boolean.FALSE))
                || (legacyDefault && ServiceFramwork.isDisableHTTP());
        thriftDisabled = Boolean.TRUE.equals(settings.getAsBoolean("thrift.disable", Boolean.TRUE))
                || (legacyDefault && ServiceFramwork.isDisabledThrift());
        dubboDisabled = Boolean.TRUE.equals(settings.getAsBoolean("dubbo.disable", Boolean.TRUE))
                || (legacyDefault && ServiceFramwork.isDisabledDubbo());
        extensions.prepare(this, settings, programmatic);
        new LoggerLoader().load(settings);
        extensions.registerAll(this, settings);
        new ModuelLoader().load(settings);
        new ServiceLoader().load(settings);
        new UtilLoader().load(settings);
        new ControllerLoader().load(settings);
        new TemplateLoader().load(settings);
        if (!thriftDisabled) {
            new ThriftLoader().load(settings);
        }
        StartupPhaseTrace.Frame guice = StartupPhaseTrace.open(enhancement, "guice");
        try {
            Injector parent = Guice.createInjector(Stage.PRODUCTION, allModules);
            injector = parent;
            if (!controllerModules.isEmpty()) {
                injector = parent.createChildInjector(controllerModules);
            }
        } finally {
            guice.close();
        }
        extensions.startAll(this, settings);
        for (int i = 0; i < startWithSystem.size(); i++) {
            injector.getInstance(startWithSystem.get(i));
        }
        if (!thriftDisabled) {
            thriftServer = injector.getInstance(ThriftServer.class);
            thriftServer.start();
            thriftStarted = true;
        }
        if (!httpDisabled) {
            httpServer = injector.getInstance(HttpServer.class);
            StartupPhaseTrace.Frame bind = StartupPhaseTrace.open(enhancement, "http.bind");
            try {
                httpServer.start();
            } finally {
                bind.close();
            }
            httpStarted = true;
            httpPort = httpServer.getHttpPort();
        }
        if (!dubboDisabled) {
            dubboServer = injector.getInstance(DubboServer.class);
        }
    }

    private void selectMode(Settings settings) {
        if (legacyDefault) {
            if (ServiceFramwork.mode == ServiceFramwork.Mode.development) {
                String configured = settings.get("mode");
                if (configured != null && configured.trim().length() > 0) {
                    ServiceFramwork.mode = ServiceFramwork.Mode.valueOf(configured.trim());
                }
            }
            mode = ServiceFramwork.mode;
            noThreadJoin = ServiceFramwork.isNoThreadJoin();
            return;
        }
        String configured = settings.get("mode");
        if (configured == null || configured.trim().length() == 0) {
            configured = ServiceFramwork.Mode.development.name();
        }
        mode = ServiceFramwork.Mode.valueOf(configured.trim());
        noThreadJoin = true;
    }

    private void finishClose() {
        boolean owner = false;
        synchronized (lifecycle) {
            if (!closeRunning && state == State.CLOSED && enhancementFinished()) {
                lifecycle.notifyAll();
                return;
            }
            if (closeRunning && closingThread != Thread.currentThread()) {
                while (closeRunning) {
                    try {
                        lifecycle.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw failure(EnhancementFailure.Category.LIFECYCLE,
                                "application close was interrupted", interrupted);
                    }
                }
                return;
            }
            if (closingThread == Thread.currentThread()) {
                return;
            }
            closeRunning = true;
            closingThread = Thread.currentThread();
            owner = true;
            if (state != State.CLOSED) {
                state = State.CLOSING;
            }
        }
        try {
            finishCloseOwned();
        } finally {
            if (owner) {
                synchronized (lifecycle) {
                    if (state != State.CLOSED && enhancementFinished()) {
                        state = State.CLOSED;
                    }
                    closeRunning = false;
                    closingThread = null;
                    lifecycle.notifyAll();
                }
            }
        }
    }

    private void finishCloseOwned() {
        List<Throwable> failures = new ArrayList<Throwable>();
        if (!serversStopped) {
            stopServer(failures);
            shutdownManaged(failures);
            if (httpServer == null || !httpServer.isRunning()) {
                httpServer = null;
                thriftServer = null;
                dubboServer = null;
                serversStopped = true;
            }
        }
        try {
            extensions.closeAll();
        } catch (Throwable thrown) {
            failures.add(thrown);
        }
        if (!detached) {
            filters = ControllerFilterPlan.compile(Collections.<Class<?>>emptyList());
            controllers.clear();
            trimModules();
            Injector created = injector;
            injector = null;
            if (legacyDefault && ServiceFramwork.injector == created) {
                ServiceFramwork.injector = null;
            }
            detached = true;
        }
        EnhancementContext toClose = enhancement;
        boolean blocked = false;
        if (toClose != null && !toClose.isClosed()) {
            try {
                toClose.completeClose();
            } catch (Throwable thrown) {
                failures.add(thrown);
                blocked = !toClose.isClosed();
            }
        }
        if (!blocked) {
            enhancement = null;
            scanService = null;
            marker = null;
            synchronized (LOCK) {
                if (RUNNING.get(targetLoader) == this) {
                    RUNNING.remove(targetLoader);
                }
                if (STARTING.get(targetLoader) == this) {
                    STARTING.remove(targetLoader);
                }
                if (!classesDefined) {
                    DEFINED.remove(targetLoader);
                }
                if (defaultContext == this) {
                    defaultContext = null;
                }
            }
            synchronized (lifecycle) {
                state = State.CLOSED;
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
        throw failure(EnhancementFailure.Category.LIFECYCLE, "application close failed",
                primary instanceof Exception ? (Exception) primary : new RuntimeException(primary));
    }

    private boolean enhancementFinished() {
        EnhancementContext current = enhancement;
        return current == null || current.isClosed();
    }

    private void trimModules() {
        while (allModules.size() > moduleBaseline) {
            allModules.remove(allModules.size() - 1);
        }
    }

    private void stopServer(List<Throwable> failures) {
        if (httpServer != null) {
            try {
                httpServer.close();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        } else if (injector != null) {
            try {
                injector.getInstance(HttpServer.class).close();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        httpStarted = false;
        if (thriftServer != null) {
            try {
                thriftServer.stop();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        thriftStarted = false;
        dubboServer = null;
    }

    private void shutdownManaged(List<Throwable> failures) {
        if (injector == null) {
            return;
        }
        try {
            HttpTransportService transport = injector.getInstance(HttpTransportService.class);
            if (transport instanceof DefaultHttpTransportService) {
                ((DefaultHttpTransportService) transport).shutdownTransport();
            }
        } catch (Throwable thrown) {
            failures.add(thrown);
        }
        try {
            injector.getInstance(ThreadPoolService.class).shutdownNow();
        } catch (Throwable thrown) {
            failures.add(thrown);
        }
    }

    private static Class<?> defaultMarker() {
        Class<?> marker = ServiceFramwork.scanService.getLoader();
        if (marker == null || marker == DefaultScanService.class) {
            marker = ServiceFramwork.class;
            ServiceFramwork.scanService.setLoader(marker);
        }
        return marker;
    }

    private static EnhancementFailure failure(EnhancementFailure.Category category, String detail) {
        return failure(category, detail, null);
    }

    private static EnhancementFailure failure(EnhancementFailure.Category category, String detail, Exception cause) {
        return new EnhancementFailure(category, null, null, "lifecycle", detail, cause);
    }

    private static void rethrow(Throwable thrown) throws Exception {
        if (thrown instanceof Error) {
            throw (Error) thrown;
        }
        if (thrown instanceof Exception) {
            throw (Exception) thrown;
        }
        throw new EnhancementFailure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                null,
                "lifecycle",
                thrown.getMessage() == null ? thrown.getClass().getName() : thrown.getMessage(),
                null);
    }

    private enum State {
        NEW,
        STARTING,
        RUNNING,
        CLOSING,
        CLOSED
    }

    /**
     * Identity-keyed weak set. {@link java.util.WeakHashMap} compares keys with
     * {@code equals}, so two live loaders that compare equal would block each other.
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

        synchronized boolean contains(ClassLoader loader) {
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

        synchronized void add(ClassLoader loader) {
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

        synchronized void remove(ClassLoader loader) {
            if (loader == null) {
                return;
            }
            expunge();
            Integer key = Integer.valueOf(System.identityHashCode(loader));
            List<LoaderKey> list = buckets.get(key);
            if (list == null) {
                return;
            }
            for (int i = list.size() - 1; i >= 0; i--) {
                ClassLoader current = list.get(i).get();
                if (current == null || current == loader) {
                    list.remove(i);
                }
            }
            if (list.isEmpty()) {
                buckets.remove(key);
            }
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
