package net.csdn.trace;

import javassist.CtClass;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceEnhancer {
    private static CSLogger logger = Loggers.getLogger(TraceEnhancer.class);

    public static void enhanceMethod(CtClass ctClass) {
//        if (Modifier.isInterface(ctClass.getModifiers())) return;
//        for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
//            if (Modifier.isAbstract(ctMethod.getModifiers())) continue;
//            try {
//                logger.info("method trace enhancer :    " + ctMethod.getLongName());
//
//                ctMethod.insertBefore(
//                        "net.csdn.trace.TraceContext traceContext= net.csdn.trace.Trace.get();" +
//                                "StackTraceElement ste = Thread.currentThread().getStackTrace()[1];" +
//                                "String methodName = ste.getClassName() + \".\" + ste.getMethodName();" +
//                                "traceContext.enterMethod(methodName);"
//                );
//
//                ctMethod.insertAfter(
//                        "net.csdn.trace.TraceContext traceContext= net.csdn.trace.Trace.get();" +
//                                "StackTraceElement ste = Thread.currentThread().getStackTrace()[1];" +
//                                "String methodName = ste.getClassName() + \".\" + ste.getMethodName();" +
//                                "traceContext.exitMethod(methodName);"
//                        , true);
//
//            } catch (CannotCompileException e) {
//                e.printStackTrace();
//            }
//        }
    }
}
