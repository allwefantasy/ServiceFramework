package net.csdn;

import com.google.inject.Injector;
import com.google.inject.Module;
import javassist.ClassPool;
import javassist.LoaderClassPath;
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
    public static Injector injector;
    public final static ScanService scanService = new DefaultScanService();
    public final static ClassPool classPool;
    public static Mode mode = Mode.development;
    public static List<Module> modules = new ArrayList<Module>();
    public static List<Module> serviceModules = new ArrayList<Module>();
    public static List<Class> startWithSystem = new ArrayList<Class>();
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

    public static void shutdown() {
        ServiceFramwork.injector.getInstance(ThreadPoolService.class).shutdownNow();
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
