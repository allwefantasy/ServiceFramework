package net.csdn;

import com.google.inject.Injector;
import com.google.inject.Module;
import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;

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

    public static void registerModule(Module module) {
        modules.add(module);
    }

    public static void registerSerivceModule(Module module) {
        serviceModules.add(module);
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
