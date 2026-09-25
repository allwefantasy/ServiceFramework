package net.csdn.jpa;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.modules.persist.mysql.DataSourceManager;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JdbcEndpointTest {

    private static final String SECRET = "synthetic-secret";

    @Test
    public void endpointDropsQueryAndUserinfo() {
        assertEquals("", JdbcEndpoints.endpoint(null));
        assertEquals("", JdbcEndpoints.endpoint(""));
        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/appdb",
                JdbcEndpoints.endpoint("jdbc:mysql://127.0.0.1:3306/appdb"));
        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/appdb",
                JdbcEndpoints.endpoint("jdbc:mysql://127.0.0.1:3306/appdb?useUnicode=true&password=" + SECRET));
        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/appdb",
                JdbcEndpoints.endpoint("jdbc:mysql://synthetic-user:" + SECRET + "@127.0.0.1:3306/appdb?password=" + SECRET));
        assertEquals(
                "jdbc:mysql://[::1]:3306/appdb",
                JdbcEndpoints.endpoint("jdbc:mysql://synthetic-user:" + SECRET + "@[::1]:3306/appdb?password=" + SECRET));
    }

    @Test
    public void propertiesLogKeepsTheConnectionUrlAndOmitsTheSecret() throws Exception {
        Settings mysql = ImmutableSettings.settingsBuilder()
                .put("host", "127.0.0.1")
                .put("port", "3306")
                .put("database", "appdb")
                .put("username", "synthetic-user")
                .put("password", SECRET)
                .put("jdbc.password", SECRET)
                .put("jdbc.user", "synthetic-user")
                .build();
        List<String> logged = new ArrayList<String>();
        Field logger = JPA.class.getDeclaredField("logger");
        logger.setAccessible(true);
        Object previous = logger.get(null);
        logger.set(null, capturingLogger(logged));
        try {
            Map<String, String> properties = JPA.properties(mysql);
            String url = properties.get("url");
            assertTrue(url, url.contains("jdbc:mysql://127.0.0.1:3306/appdb?"));
            assertTrue(url, url.contains("password=" + SECRET));
            assertTrue(url, url.contains("user=synthetic-user"));
            assertEquals(SECRET, properties.get("password"));
        } finally {
            logger.set(null, previous);
        }
        assertFalse(logged.isEmpty());
        for (int i = 0; i < logged.size(); i++) {
            String line = logged.get(i);
            assertTrue(line, line.contains("127.0.0.1:3306/appdb"));
            assertFalse(line, line.contains("?"));
            assertFalse(line, line.contains(SECRET));
            assertFalse(line, line.contains("synthetic-user"));
            assertFalse(line, line.contains("@"));
        }
    }

    @Test
    public void datasourceFailureUsesTheEndpointOnly() throws Exception {
        Method method = DataSourceManager.class.getDeclaredMethod("datasourceFailure", String.class, Exception.class);
        method.setAccessible(true);
        String url = "jdbc:mysql://synthetic-user:" + SECRET + "@127.0.0.1:3306/appdb?password=" + SECRET;
        Exception cause = new Exception("boom");
        RuntimeException failure = (RuntimeException) method.invoke(null, url, cause);
        assertEquals("can not create datasource jdbc:mysql://127.0.0.1:3306/appdb", failure.getMessage());
        assertFalse(failure.getMessage().contains(SECRET));
        assertFalse(failure.getMessage().contains("?"));
        assertEquals(cause, failure.getCause());
        RuntimeException missing = (RuntimeException) method.invoke(null, new Object[]{null, cause});
        assertEquals("can not create datasource", missing.getMessage());
    }

    private static CSLogger capturingLogger(final List<String> logged) {
        return (CSLogger) Proxy.newProxyInstance(
                CSLogger.class.getClassLoader(),
                new Class<?>[]{CSLogger.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (("info".equals(method.getName()) || "warn".equals(method.getName())
                                || "error".equals(method.getName()) || "debug".equals(method.getName()))
                                && args != null && args.length > 0 && args[0] instanceof String) {
                            logged.add((String) args[0]);
                        }
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) {
                            return Boolean.TRUE;
                        }
                        if (type == int.class) {
                            return Integer.valueOf(0);
                        }
                        if (type == String.class) {
                            return "";
                        }
                        return null;
                    }
                });
    }
}
