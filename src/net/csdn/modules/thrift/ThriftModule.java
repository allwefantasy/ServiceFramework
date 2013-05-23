package net.csdn.modules.thrift;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import net.csdn.modules.http.HttpServer;

/**
 * 5/23/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ThriftModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ThriftServer.class).in(Singleton.class);
    }
}
