package net.csdn.common.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;

import java.io.Closeable;
import java.security.ProtectionDomain;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One enhancement session: its own {@link ClassPool}, {@link ClassDefiner}, attributes
 * and tracked resources. There is no process-wide context in this module. Request
 * threads call {@link #activate()} and async work must receive this instance and
 * activate it on that thread. {@link #currentOrNull()} only sees the calling thread.
 * <p>
 * Thread-safety boundary: {@link #activate()}, {@link Scope#close()} bookkeeping and
 * shutdown admission are serialized on one monitor, so a scope can never be opened
 * while shutdown is reserved or the context is closing. Enhancement work inside a
 * scope is confined to the scope's owning thread and is not internally synchronized.
 * Resource release does not run while that monitor is held across a foreign thread
 * join; {@link #admitShutdown()} only reserves, and {@link #completeClose()} releases.
 * <p>
 * {@link #close()} closes tracked {@link Closeable}s and detaches tracked
 * {@link CtClass} metadata in reverse order, drops the {@link ClassPool}'s loader
 * reference and the {@link ClassDefiner}'s anchor and defined records, then releases
 * the pool and definer themselves. Secondary failures ride on the primary one as
 * suppressed. Detach releases Javassist's class file cache; it does not unload a
 * class already defined in the target loader, and for full reclamation the caller
 * must still drop its own reference to this context.
 */
public final class EnhancementContext implements AutoCloseable {

    private static final ThreadLocal<Deque<Scope>> CURRENT = new ThreadLocal<Deque<Scope>>();

    private final Object lifecycleLock = new Object();
    private ClassLoader targetLoader;
    private ClassPool classPool;
    private ClassDefiner classDefiner;
    private EnhancementDiagnostics diagnostics;
    private final LoaderClassPath loaderPath;
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    private final List<CtClass> types = new ArrayList<CtClass>();
    private final List<Closeable> resources = new ArrayList<Closeable>();
    private final Set<CtClass> seenTypes = Collections.newSetFromMap(new IdentityHashMap<CtClass, Boolean>());
    private final Set<Closeable> seenResources = Collections.newSetFromMap(new IdentityHashMap<Closeable, Boolean>());
    private final Set<String> appliedClasses = new HashSet<String>();
    private final Set<String> capturedOriginals = new HashSet<String>();
    private final List<EnhancementObserver> observers = new ArrayList<EnhancementObserver>();
    private final List<EnhancementPlan.Execution> executions = new ArrayList<EnhancementPlan.Execution>();
    private int activations;
    private Gate gate = Gate.OPEN;
    private volatile boolean closed;

    /**
     * Result of {@link #admitShutdown()}. The check and the reservation happen
     * on the same monitor as {@link #activate()}.
     */
    public enum ShutdownAdmission {
        /** A scope is still open. The context is unchanged and still accepts {@link #activate()}. */
        REJECTED_ACTIVE,
        /** This caller reserved shutdown. {@link #activate()} now fails. Call {@link #completeClose()}. */
        RESERVED,
        /** Another caller already reserved shutdown and has not finished {@link #completeClose()}. */
        IN_PROGRESS,
        /** Resources were already released. */
        CLOSED
    }

    private enum Gate {
        OPEN,
        CLOSING,
        CLOSED
    }

    private EnhancementContext(ClassLoader targetLoader, EnhancementDiagnostics diagnostics) {
        this.targetLoader = targetLoader;
        this.diagnostics = diagnostics == null ? EnhancementDiagnostics.disabled() : diagnostics;
        this.diagnostics.bindTarget(targetLoader);
        this.classPool = new ClassPool(false);
        this.classPool.appendSystemPath();
        this.loaderPath = new LoaderClassPath(targetLoader);
        this.classPool.insertClassPath(loaderPath);
        this.classDefiner = new ClassDefiner(this);
    }

    public static EnhancementContext open(ClassLoader targetLoader) {
        return open(targetLoader, EnhancementDiagnostics.disabled());
    }

    public static EnhancementContext open(ClassLoader targetLoader, EnhancementDiagnostics diagnostics) {
        if (targetLoader == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "open",
                    "target loader is required",
                    null);
        }
        return new EnhancementContext(targetLoader, diagnostics);
    }

    public ClassLoader targetLoader() {
        ensureOpen();
        return targetLoader;
    }

    public ClassPool classPool() {
        ensureOpen();
        return classPool;
    }

    public ClassDefiner classDefiner() {
        ensureOpen();
        return classDefiner;
    }

    public EnhancementDiagnostics diagnostics() {
        ensureOpen();
        return diagnostics;
    }

    /**
     * Registers an observer. Observers must be added before the first
     * {@link EnhancementPlan#apply} whose originals they need. The same instance
     * is not added twice.
     */
    public void addObserver(EnhancementObserver observer) {
        ensureOpen();
        if (observer == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "observer",
                    "observer is required",
                    null);
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    boolean hasObservers() {
        return !observers.isEmpty();
    }

    boolean originalCaptured(String className) {
        return capturedOriginals.contains(className);
    }

    void markOriginalCaptured(String className) {
        capturedOriginals.add(className);
    }

    void notifyBeforeFirstMutation(String className, byte[] originalBytecode) {
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).beforeFirstMutation(className, originalBytecode);
        }
    }

    void notifyAfterRule(
            String className,
            String ruleId,
            int version,
            List<EnhancementDiagnostics.MethodChange> changes) {
        List<EnhancementDiagnostics.MethodChange> view = changes == null
                ? Collections.<EnhancementDiagnostics.MethodChange>emptyList()
                : changes;
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).afterRule(className, ruleId, version, view);
        }
    }

    public Object getAttribute(String key) {
        ensureOpen();
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        ensureOpen();
        if (key == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "attribute",
                    "attribute key is required",
                    null);
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public CtClass makeClass(String name) {
        ensureOpen();
        CtClass created = classPool.makeClass(name);
        created.getClassFile().setMajorVersion(ClassFile.JAVA_8);
        created.getClassFile().setMinorVersion(0);
        track(created);
        return created;
    }

    public CtClass get(String className) throws NotFoundException {
        ensureOpen();
        CtClass type = classPool.get(className);
        track(type);
        return type;
    }

    public void track(CtClass type) {
        ensureOpen();
        if (type == null || !seenTypes.add(type)) {
            return;
        }
        types.add(type);
    }

    public void track(Closeable resource) {
        ensureOpen();
        if (resource == null || !seenResources.add(resource)) {
            return;
        }
        resources.add(resource);
    }

    /**
     * Defines into {@link #targetLoader()} using the anchor protection domain.
     * Rules must not call this; the caller does, after the plan has finished.
     */
    public Class<?> define(CtClass type) {
        ensureOpen();
        if (type == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "define",
                    "class is required",
                    null);
        }
        Class<?> anchor = classDefiner.lookupAnchor(ClassDefiner.packageName(type), targetLoader);
        ProtectionDomain domain = anchor == null ? null : anchor.getProtectionDomain();
        return classDefiner.define(type, targetLoader, domain);
    }

    public Scope activate() {
        synchronized (lifecycleLock) {
            if (closed || gate != Gate.OPEN) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.LIFECYCLE,
                        null,
                        null,
                        "activate",
                        "enhancement context is closed",
                        null);
            }
            Thread owner = Thread.currentThread();
            Deque<Scope> stack = CURRENT.get();
            if (stack == null) {
                stack = new ArrayDeque<Scope>();
                CURRENT.set(stack);
            }
            Scope scope = new Scope(this, owner);
            stack.push(scope);
            activations++;
            return scope;
        }
    }

    /**
     * Atomically refuses shutdown while a scope is open, or reserves shutdown so
     * later {@link #activate()} calls fail. Reservation does not release resources
     * and does not block for another thread to finish work.
     */
    public ShutdownAdmission admitShutdown() {
        synchronized (lifecycleLock) {
            if (closed || gate == Gate.CLOSED) {
                return ShutdownAdmission.CLOSED;
            }
            if (gate == Gate.CLOSING) {
                return ShutdownAdmission.IN_PROGRESS;
            }
            if (activations != 0) {
                return ShutdownAdmission.REJECTED_ACTIVE;
            }
            gate = Gate.CLOSING;
            return ShutdownAdmission.RESERVED;
        }
    }

    /**
     * True after {@link #admitShutdown()} has reserved shutdown, including once
     * {@link #close()} has finished. False while new scopes are still accepted.
     */
    public boolean isShutdownAdmitted() {
        synchronized (lifecycleLock) {
            return gate != Gate.OPEN;
        }
    }

    /**
     * Releases tracked resources after shutdown was reserved. If shutdown is not
     * reserved yet, this admits it first and throws, leaving the context unchanged,
     * when a scope is still open. A second call after resources are released returns.
     * Callers that still need to join foreign threads must do that between
     * {@link #admitShutdown()} and this method, not while holding {@code lifecycleLock}.
     */
    public void completeClose() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            if (gate == Gate.OPEN) {
                if (activations != 0) {
                    throw new EnhancementFailure(
                            EnhancementFailure.Category.LIFECYCLE,
                            null,
                            null,
                            "close",
                            "close the active scopes on their owning threads before closing the context",
                            null);
                }
                gate = Gate.CLOSING;
            }
            releaseResourcesLocked();
        }
    }

    public static EnhancementContext currentOrNull() {
        Deque<Scope> stack = CURRENT.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek().context();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Read-only execution report of this context. It stays available after
     * {@link #close()} so callers can audit what actually ran.
     */
    public List<EnhancementPlan.Execution> executions() {
        synchronized (lifecycleLock) {
            return Collections.unmodifiableList(new ArrayList<EnhancementPlan.Execution>(executions));
        }
    }

    /**
     * Records that this context started enhancing {@code className}. Called once by
     * {@link EnhancementPlan#apply}; any second attempt for the same class name in
     * this context, including after a partial failure or from another plan, is a
     * conflict because the {@link CtClass} may already have been edited.
     */
    void beginEnhancement(String className) {
        synchronized (lifecycleLock) {
            ensureOpen();
            if (!appliedClasses.add(className)) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.CONFLICT,
                        className,
                        null,
                        "apply",
                        "class was already enhanced in this context",
                        null);
            }
        }
    }

    void recordExecution(String className, String ruleId, int version) {
        synchronized (lifecycleLock) {
            executions.add(new EnhancementPlan.Execution(className, ruleId, version));
        }
    }

    @Override
    public void close() {
        ShutdownAdmission admission = admitShutdown();
        if (admission == ShutdownAdmission.REJECTED_ACTIVE) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "close",
                    "close the active scopes on their owning threads before closing the context",
                    null);
        }
        if (admission == ShutdownAdmission.CLOSED) {
            return;
        }
        if (admission == ShutdownAdmission.IN_PROGRESS) {
            waitUntilClosed();
            return;
        }
        completeClose();
    }

    private void waitUntilClosed() {
        synchronized (lifecycleLock) {
            while (!closed) {
                try {
                    lifecycleLock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new EnhancementFailure(
                            EnhancementFailure.Category.LIFECYCLE,
                            null,
                            null,
                            "close",
                            "enhancement close was interrupted",
                            interrupted);
                }
            }
        }
    }

    private void releaseResourcesLocked() {
        if (closed) {
            lifecycleLock.notifyAll();
            return;
        }
        closed = true;
        List<Throwable> failures = new ArrayList<Throwable>();
        try {
            for (int i = resources.size() - 1; i >= 0; i--) {
                try {
                    resources.get(i).close();
                } catch (Throwable thrown) {
                    failures.add(thrown);
                }
            }
            for (int i = types.size() - 1; i >= 0; i--) {
                try {
                    types.get(i).detach();
                } catch (Throwable thrown) {
                    failures.add(thrown);
                }
            }
            try {
                classPool.removeClassPath(loaderPath);
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
            try {
                // Events stay on this diagnostics instance if the write fails, so the
                // caller can flush the same object again. This field is cleared below.
                diagnostics.flush();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
            try {
                classDefiner.markClosed();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
            attributes.clear();
            resources.clear();
            types.clear();
            seenResources.clear();
            seenTypes.clear();
            appliedClasses.clear();
            capturedOriginals.clear();
            observers.clear();
            classPool = null;
            classDefiner = null;
            targetLoader = null;
            diagnostics = null;
            gate = Gate.CLOSED;
        } finally {
            lifecycleLock.notifyAll();
        }
        if (failures.isEmpty()) {
            return;
        }
        Throwable primary = failures.get(0);
        if (primary instanceof Error || primary instanceof RuntimeException) {
            for (int i = 1; i < failures.size(); i++) {
                primary.addSuppressed(failures.get(i));
            }
            if (primary instanceof Error) {
                throw (Error) primary;
            }
            throw (RuntimeException) primary;
        }
        EnhancementFailure failure = new EnhancementFailure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                null,
                "close",
                "resource close failed",
                primary instanceof Exception ? (Exception) primary : new RuntimeException(primary));
        for (int i = 1; i < failures.size(); i++) {
            failure.addSuppressed(failures.get(i));
        }
        throw failure;
    }

    void ensureOpen() {
        if (closed) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "use",
                    "enhancement context is closed",
                    null);
        }
    }

    void scopeClosed(Scope scope) {
        synchronized (lifecycleLock) {
            activations--;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final EnhancementContext context;
        private final Thread owner;
        private boolean closed;

        Scope(EnhancementContext context, Thread owner) {
            this.context = context;
            this.owner = owner;
        }

        public EnhancementContext context() {
            return context;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.LIFECYCLE,
                        null,
                        null,
                        "close",
                        "scope opened on " + owner.getName() + " cannot be closed on " + Thread.currentThread().getName(),
                        null);
            }
            Deque<Scope> stack = CURRENT.get();
            if (stack == null || stack.peek() != this) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.LIFECYCLE,
                        null,
                        null,
                        "close",
                        "scope closed out of order",
                        null);
            }
            stack.pop();
            closed = true;
            context.scopeClosed(this);
            if (stack.isEmpty()) {
                CURRENT.remove();
            }
        }
    }
}
