package test2;

import com.example.controller.api.TagController;
import net.csdn.common.collections.WowCollections;
import net.csdn.junit.BaseServiceTest;
import net.csdn.modules.dubbo.DubboServer;
import net.csdn.modules.dubbo.demo.server.DemoService;
import net.csdn.modules.http.RestRequest;
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
        DemoService demoService = findService(DubboServer.class).getBean("demoService", DemoService.class);
        System.out.println(demoService.sayHello("你好，太脑残"));

    }

    @Test
    public void test2() {
        TagController tagController = findService(DubboServer.class).getBean("restDemoService", TagController.class);
        System.out.println(tagController.sayHello(RestRequest.Method.GET, WowCollections.map("kitty", "你好，太脑残")).getContent());
        System.out.println(tagController.sayHello(RestRequest.Method.GET, WowCollections.map("kitty", "你好，太脑残")).getContent());

        System.out.println(tagController.sayHello2("{}", WowCollections.map("kitty", "你好，太脑残")).getContent());

        System.out.println(tagController.sayHello3("哇塞，天才呀").getContent());

    }
}
