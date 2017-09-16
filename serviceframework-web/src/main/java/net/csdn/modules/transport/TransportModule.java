package net.csdn.modules.transport;
/**
 * BlogInfo: WilliamZhu
 * Date: 12-5-29
 * Time: 下午5:09
 */

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import net.csdn.bootstrap.loader.impl.ServiceLoader;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;


public class TransportModule extends AbstractModule {
    private CSLogger logger = Loggers.getLogger(TransportModule.class);

    @Override
    protected void configure() {
        try {
            bind(HttpTransportService.class).to(DefaultHttpTransportService.class).in(Singleton.class);
        } catch (Exception e) {
            logger.warn("HttpTransportService bind exception", e.getCause());
        }
    }
}
