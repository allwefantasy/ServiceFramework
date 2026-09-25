package net.csdn;

import com.google.inject.Injector;
import com.google.inject.Module;
import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.modules.threadpool.ThreadPoolService;

import java.util.ArrayList;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:35
 */
public class ServiceFramwork {
    /**
     * Alias of the default application's injector. A second application does not
     * write this field. Request code should call {@link #currentInjector()}.
     */
    @Deprecated
    public static Injector injector;
    /**
     * Classpath scanner used by the default application. Other applications own
     * a separate {@link ScanService}.
     */
    @Deprecated
    public final static ScanService scanService = new DefaultScanService();
    /**
     * Legacy pool created at class initialization. Enhancement uses the pool on
     * the current {@link net.csdn.common.enhancer.EnhancementContext}, not this field.
     */
    @Deprecated
    public final static ClassPool classPool;
    public static Mode mode = Mode.development;
    /**
     * Modules registered for the default application before it starts.
     */
    @Deprecated
    public static List<Module> modules = new ArrayList<Module>();
    @Deprecated
    public static List<Module> serviceModules = new ArrayList<Module>();
    @Deprecated
    public static List<Class> startWithSystem = new ArrayList<Class>();
    @Deprecated
    public static List<Module> AllModules = new ArrayList<Module>();

    private static boolean DisableHTTP = false;
    private static boolean DisableThrift = false;
    private static boolean DisableDubbo = false;
    private static boolean NoThreadJoin = false;

    private static String applicaionYamlName = "application.yml";

    public static void applicaionYamlName(String applicaionYamlName) {
        ServiceFramwork.applicaionYamlName = applicaionYamlName;
    }

    public static String applicaionYamlName() {
        return ServiceFramwork.applicaionYamlName;
    }

    public static void disableHTTP() {
        DisableHTTP = true;
    }

    public static void disableThrift() {
        DisableThrift = true;
    }

    public static void disableDubbo() {
        DisableDubbo = true;
    }

    public static boolean isDisableHTTP() {
        return DisableHTTP;
    }

    public static boolean isDisabledThrift() {
        return DisableThrift;
    }

    public static boolean isDisabledDubbo() {
        return DisableDubbo;
    }

    public static boolean isNoThreadJoin() {
        return NoThreadJoin;
    }

    public static void enableNoThreadJoin() {
        NoThreadJoin = true;
    }


    public static void registerModule(Module module) {
        modules.add(module);
    }

    public static void registerSerivceModule(Module module) {
        serviceModules.add(module);
    }

    public static void registerStartWithSystemServices(Class clzz) {
        startWithSystem.add(clzz);
    }

    /**
     * Injector of the application bound to this thread. If a scope is active it
     * must belong to an application; this method does not fall back to
     * {@link #injector} in that case. With no scope, {@link #injector} is the
     * default application alias. {@code serviceframework.dispatcher.ServiceInj}
     * calls this method and does not keep its own injector.
     */
    public static Injector currentInjector() {
        if (ApplicationContext.foreignScope()) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "injector",
                    "active enhancement context is not an application context",
                    null);
        }
        ApplicationContext current = ApplicationContext.currentOrNull();
        if (current != null) {
            if (current.injector() == null) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.LIFECYCLE,
                        null,
                        null,
                        "injector",
                        "application injector is not ready",
                        null);
            }
            return current.injector();
        }
        EnhancementContext enhancement = EnhancementContext.currentOrNull();
        if (enhancement != null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "injector",
                    "active enhancement context is not an application context",
                    null);
        }
        if (injector == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "injector",
                    "no application injector is active",
                    null);
        }
        return injector;
    }

    public static ScanService currentScanService() {
        ApplicationContext current = ApplicationContext.currentOrNull();
        if (current != null) {
            return current.scanService();
        }
        return scanService;
    }

    public static Mode currentMode() {
        ApplicationContext current = ApplicationContext.currentOrNull();
        if (current != null && current.mode() != null) {
            return current.mode();
        }
        return mode;
    }

    public static void shutdown() {
        ApplicationContext current = ApplicationContext.defaultContext();
        if (current != null && !current.isClosed()) {
            current.close();
            return;
        }
        if (injector != null) {
            injector.getInstance(ThreadPoolService.class).shutdownNow();
        }
    }

    public static enum Mode {
        development, production, test
    }

    static {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(new LoaderClassPath(ServiceFramwork.class.getClassLoader()));
    }
}
