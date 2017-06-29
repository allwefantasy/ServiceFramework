package net.csdn.bootstrap.loader.impl;

import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.env.Environment;
import net.csdn.common.settings.Settings;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.util.Properties;

/**
 * 6/29/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class TemplateLoader implements Loader {
    @Override
    public void load(Settings settings) throws Exception {
        Environment environment = new Environment(settings);
        if (settings.getAsBoolean("application.template.engine.enable", false)) {
            Properties properties = new Properties();

            if (settings.getAsBoolean("serviceframework.template.loader.classpath.enable", false)) {
                properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                properties.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            } else {
                properties.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, environment.templateDirFile().getPath());
            }

            properties.setProperty("input.encoding", "utf-8");
            properties.setProperty("output.encoding", "utf-8");
            properties.setProperty("runtime.log", environment.logsFile().getPath() + "/template");
            Velocity.init(properties);
        }
    }
}
