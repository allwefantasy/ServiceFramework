package net.csdn;

import com.google.inject.Injector;
import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.csdn.modules.scan.DefaultScanService;
import net.csdn.modules.scan.ScanService;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:35
 */
public class ServiceFramwork {
    public static Injector injector;
    public static ScanService scanService = new DefaultScanService();
    public static ClassPool classPool;
    public static Mode mode = Mode.development;

    public static enum Mode {
        development, production, test
    }

    static {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(new LoaderClassPath(ServiceFramwork.class.getClassLoader()));
    }
}
