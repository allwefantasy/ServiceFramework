package net.csdn.bootstrap;

import net.csdn.ServiceFramwork;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

public class DefaultContextCloseTest {
    @Test
    public void closingTheDefaultContextDropsTheStaticReference() throws Exception {
        ServiceFramwork.Mode previous = ServiceFramwork.mode;
        ApplicationContext created = null;
        try {
            created = ApplicationContext.bootstrapDefault(settings());
            Assert.assertSame(created, ApplicationContext.defaultContext());
            Assert.assertTrue(created.isRunning());
            created.close();
            Assert.assertTrue(created.isClosed());
            Assert.assertNull(ApplicationContext.defaultContext());
            created.close();
            Assert.assertNull(ApplicationContext.defaultContext());
        } finally {
            ServiceFramwork.mode = previous;
            if (created != null && !created.isClosed()) {
                created.close();
            }
            if (ApplicationContext.defaultContext() == created) {
                Field field = ApplicationContext.class.getDeclaredField("defaultContext");
                field.setAccessible(true);
                field.set(null, null);
            }
        }
    }

    private static Settings settings() {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("path.conf", configDir().getAbsolutePath())
                .put("path.logs", new File("target/logs").getAbsolutePath())
                .put("cluster.name", "sf-web-lifecycle")
                .put("test.datasources.mysql.disable", "true")
                .put("test.datasources.mongodb.disable", "true")
                .put("test.datasources.redis.disable", "true")
                .put("http.disable", "true")
                .put("thrift.disable", "true")
                .put("dubbo.disable", "true")
                .put("application.template.engine.enable", "false")
                .put("application.api.qps.enable", "false")
                .put("application.log.enable", "false")
                .put("application.controller", "")
                .put("application.controller.default", "")
                .put("application.controllerNames", "")
                .put("application.service", "")
                .put("application.util", "")
                .put("qpslimit.enable", "false")
                .build();
    }

    private static File configDir() {
        File direct = new File("config");
        if (new File(direct, "logging.yml").isFile()) {
            return direct;
        }
        File parent = new File("../config");
        if (new File(parent, "logging.yml").isFile()) {
            return parent;
        }
        throw new IllegalStateException("logging.yml was not found from " + new File("").getAbsolutePath());
    }
}
