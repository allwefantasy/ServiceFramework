package net.csdn.modules.http;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:55
 */
public class HttpModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RestController.class).toInstance(new RestController());
        bind(HttpServer.class).in(Singleton.class);
    }
}
