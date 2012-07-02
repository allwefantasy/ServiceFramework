package net.csdn;

import com.google.inject.Injector;
import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.csdn.modules.scan.DefaultScanService;
import net.csdn.modules.scan.ScanService;

/**
 * User: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:35
 */
public class ServiceFramwork {
    public static Injector injector;
    public static ScanService scanService = new DefaultScanService();
    public static ClassPool classPool;

    static {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(new LoaderClassPath(ServiceFramwork.class.getClassLoader()));
    }
}
