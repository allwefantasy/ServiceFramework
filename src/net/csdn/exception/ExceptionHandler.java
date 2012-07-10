package net.csdn.exception;

import java.lang.reflect.InvocationTargetException;

/**
 * User: WilliamZhu
 * Date: 12-7-10
 * Time: 上午8:25
 */
public class ExceptionHandler {
    public static  void renderHandle(Exception e) throws Exception {
        if (e instanceof InvocationTargetException) {
            InvocationTargetException invocationTargetException = (InvocationTargetException) e;
            if (invocationTargetException.getTargetException() instanceof RenderFinish) {
                return;
            }
        } else {
            throw e;
        }
        if (!(e instanceof RenderFinish)) throw e;
    }
}
