package net.csdn.junit;

import net.csdn.bootstrap.Bootstrap;
import org.junit.After;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-17
 * Time: 下午10:27
 */
public class JettyTest extends IocTest {


    @After
    public void tearDown() throws Exception {
        Bootstrap.shutdown();
    }

    public void runTargetServer(Runnable runnable) {
        //启动第三方HttpServer服务
        Thread runServer = new Thread(runnable);
        runServer.start();
        while (runServer.getState() != Thread.State.WAITING) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {

            }
        }
    }
}
