package test2;

import net.csdn.junit.BaseServiceTest;
import net.csdn.modules.dubbo.DubboServer;
import net.csdn.modules.dubbo.demo.server.DemoService;
import org.junit.Test;

/**
 * 6/25/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TestDubbo extends BaseServiceTest {
    static {
        initEnv(TestDubbo.class);
    }

    @Test
    public void test1() {
       DemoService demoService = findService(DubboServer.class).getBean("demoService",DemoService.class);
       System.out.println(demoService.sayHello("你好，太脑残"));

    }
}
