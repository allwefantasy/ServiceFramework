package runner;

import javassist.bytecode.ClassFile;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.bootstrap.ClassFiles;
import net.csdn.common.settings.Settings;
import org.junit.runners.Suite;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-27
 * Time: 下午3:29
 */
public class DynamicSuite extends Suite {
    private static Class[] testClasses = new Class[]{};

    static {
        try {
            initEnv();
        } catch (Exception failure) {
            throw abortInitialization(failure);
        }
    }

    public DynamicSuite(Class<?> setupClass) throws Exception {
        super(setupClass, findTestClass());
    }

    public static void initEnv() throws Exception {
        ServiceFramwork.mode = ServiceFramwork.Mode.test;
        ServiceFramwork.enableNoThreadJoin();
        Bootstrap.configureSystem();
    }

    public static Class[] findTestClass() throws Exception {
        Settings settings = ServiceFramwork.currentInjector().getInstance(Settings.class);
        List<Class> classList = new ArrayList<Class>();
        String testPackage = settings.get("application.test");
        if (testPackage != null && testPackage.trim().length() > 0) {
            collectTests(classList, testPackage);
        }
        Class[] classes = testClasses.length > 0 ? testClasses : new Class[classList.size()];
        classList.toArray(classes);
        return classes;
    }

    private static void collectTests(List<Class> classList, String testPackage) throws Exception {
        List<InputStream> streams = ServiceFramwork.currentScanService().scanArchives(testPackage);
        Throwable failure = null;
        try {
            for (int i = 0; i < streams.size(); i++) {
                InputStream stream = streams.get(i);
                ClassFile parsed = ClassFiles.parse(ClassFiles.read(stream));
                classList.add(Class.forName(parsed.getName(), false, DynamicSuite.class.getClassLoader()));
            }
        } catch (Throwable thrown) {
            failure = thrown;
        } finally {
            IOException closeFailure = closeOwned(streams);
            if (closeFailure != null) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    private static IOException closeOwned(List<InputStream> streams) {
        IOException failure = null;
        if (streams == null) {
            return null;
        }
        for (int i = 0; i < streams.size(); i++) {
            InputStream stream = streams.get(i);
            if (stream == null) {
                continue;
            }
            try {
                stream.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    private static Error abortInitialization(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof Error) {
            return (Error) cause;
        }
        return new ExceptionInInitializerError(cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
