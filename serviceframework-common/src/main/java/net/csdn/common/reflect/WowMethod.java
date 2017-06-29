package net.csdn.common.reflect;

import java.lang.reflect.Method;

/**
 * User: WilliamZhu
 * Date: 12-11-6
 * Time: 下午1:54
 */
public class WowMethod {
    private Method method;
    private Object target;

    public WowMethod(Object target, Method method) {
        this.method = method;
        this.target = target;
    }

    public Object invoke(Object... params) {
        try {
            method.setAccessible(true);
            return method.invoke(target, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
