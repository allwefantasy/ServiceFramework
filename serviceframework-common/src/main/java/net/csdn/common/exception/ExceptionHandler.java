package net.csdn.common.exception;

import java.lang.reflect.InvocationTargetException;

/**
 * User: WilliamZhu
 * Date: 12-7-10
 * Time: 上午8:25
 */
public class ExceptionHandler {
    public static void renderHandle(Exception e) throws Exception {
        Exception temp = new Exception();
        if (e instanceof InvocationTargetException) {
            temp = (InvocationTargetException) e;
            for (int i = 0; i < 10; i++) {
                InvocationTargetException wow = (InvocationTargetException) temp;
                if (!(wow.getTargetException() instanceof Exception)) {
                    wow.getTargetException().printStackTrace();
                    throw wow;
                }
                temp = (Exception) wow.getTargetException();
                if (temp instanceof RenderFinish) {
                    return;
                }
                if (!(temp instanceof InvocationTargetException)) {
                    break;
                }
            }
        } else if (e instanceof RenderFinish) {
            return;
        } else {
            throw e;
        }

        throw temp;
    }
}
