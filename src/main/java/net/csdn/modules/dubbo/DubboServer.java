package net.csdn.modules.dubbo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.csdn.ServiceFramwork;
import net.csdn.common.env.Environment;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 6/24/15 WilliamZhu(allwefantasy@gmail.com)
 */
@Singleton
public class DubboServer {
    private CSLogger logger = Loggers.getLogger(getClass());
    private Settings settings;
    private ApplicationContext ctx;

    public  <T> T getBean(String name,Class<T> clzzz){
        return (T)ctx.getBean(name);
    }

    @Inject
    public DubboServer(Settings settings, Environment env) {
        this.settings = settings;
        try {
            // 初始化Spring
            List<String> configFiles = new ArrayList<String>();
            for (File file : env.configFile().listFiles()) {
                if(ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)){
                    if (file.getName().endsWith("_client.xml")) {
                        configFiles.add("file:" + file.getPath());
                    }
                }else{
                    if (file.getName().endsWith("_server.xml")) {
                        configFiles.add("file:" + file.getPath());
                    }
                }
            }
            if(configFiles.size()==0)return;
            String[] configFilesS = new String[configFiles.size()];
            configFiles.toArray(configFilesS);
            ctx = new FileSystemXmlApplicationContext(configFilesS);
            logger.info("dubbo provider is running...");
        } catch (Exception ex) {
            logger.error("start dubbo fail", ex);
        }
    }

}
