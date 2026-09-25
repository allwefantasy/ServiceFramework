package net.csdn.remotenotes;

import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.JPQL;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Static query methods are added to leaf models at startup. Source compiled
 * against {@code Model} would bind those calls to the throwing superclass
 * method, so controllers go through the enhanced class.
 */
public final class OrmCalls {
    private OrmCalls() {
    }

    public static JPABase create(Class<?> type, Map params) {
        return (JPABase) call(type, "create", new Class<?>[]{Map.class}, new Object[]{params});
    }

    public static JPABase findById(Class<?> type, Object id) {
        return (JPABase) call(type, "findById", new Class<?>[]{Object.class}, new Object[]{id});
    }

    public static JPQL where(Class<?> type, String condition, Map params) {
        return (JPQL) call(type, "where", new Class<?>[]{String.class, Map.class}, new Object[]{condition, params});
    }

    public static long count(Class<?> type) {
        return ((Number) call(type, "count", new Class<?>[0], new Object[0])).longValue();
    }

    private static Object call(Class<?> type, String name, Class<?>[] types, Object[] args) {
        try {
            return type.getMethod(name, types).invoke(null, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(type.getSimpleName() + "." + name, cause);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(type.getSimpleName() + "." + name, ex);
        }
    }
}
