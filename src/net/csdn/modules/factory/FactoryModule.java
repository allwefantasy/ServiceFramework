package net.csdn.modules.factory;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import org.apache.lucene.index.Engine;
import org.apache.lucene.index.IndexEngine;
import org.apache.lucene.index.RsyncIndexEngine;

/**
 * User: WilliamZhu
 * Date: 12-6-5
 * Time: 上午10:27
 */
public class FactoryModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FactoryModuleBuilder().implement(Engine.class, Names.named("indexEngine"), IndexEngine.class)
                .implement(Engine.class, Names.named("rsyncIndexEngine"), RsyncIndexEngine.class).build(CommonFactory.class));
    }
}
