package net.csdn.modules.transport;/**
 * User: WilliamZhu
 * Date: 12-5-29
 * Time: 下午5:09
 */

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;


public class TransportModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HttpTransportService.class).to(DefaultHttpTransportService.class).in(Singleton.class);
        bind(SearchTransportService.class).to(DefaultSearchTransportService.class).in(Singleton.class);
    }
}
