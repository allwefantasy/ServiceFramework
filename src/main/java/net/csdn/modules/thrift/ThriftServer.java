package net.csdn.modules.thrift;

import com.google.inject.Inject;
import net.csdn.ServiceFramwork;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;


/**
 * 5/23/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ThriftServer {

    private CSLogger logger = Loggers.getLogger(getClass());
    private Settings settings;

    private List<TServer> servers = new ArrayList<TServer>();

    private final static String prefix = "thrift.services";

    /*
       Support for Multiplexing Services on any Transport, Protocol and Server
       https://issues.apache.org/jira/browse/THRIFT-563
     */
    @Inject
    public ThriftServer(Settings settings) {
        this.settings = settings;
        boolean disableThrift = settings.getAsBoolean("thrift.disable", false);
        if (disableThrift || ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) return;
        Map<String, String> services = settings.getByPrefix(prefix + ".").getAsMap();
        Map<String, Map<String, String>> newServices = new HashMap<String, Map<String, String>>();
        for (Map.Entry<String, String> entry : services.entrySet()) {
            String[] split = entry.getKey().split("\\.");
            String className = split[0].replaceAll("_", ".");
            if (split.length > 1) {
                if (newServices.containsKey(className)) {
                    newServices.get(className).put(entry.getKey().substring(className.length()), entry.getValue());
                } else {
                    newServices.put(className, map(entry.getKey().substring(className.length()), entry.getValue()));
                }

            }

        }

        for (Map.Entry<String, Map<String, String>> entry : newServices.entrySet()) {
            try {
                String impClassName = entry.getKey();
                String parent = (String) entry.getValue().get(".interface");
                Class clzz = Class.forName(impClassName);
                if (parent == null) {
                    Class[] inters = clzz.getInterfaces();
                    for (Class iter : inters) {
                        if (iter.getName().endsWith("Iface"))
                            parent = clzz.getInterfaces()[0].getName();
                    }

                }
                Class processor = Class.forName(parent.split("\\$")[0] + "$Processor");
                servers.add(
                        new TThreadPoolServer(
                                new TThreadPoolServer
                                        .Args(

                                        new TServerSocket(
                                                Integer.parseInt(entry.getValue().get(".port"))
                                        ))
                                        .protocolFactory(new TCompactProtocol.Factory())
                                        .processor((TProcessor) processor.getConstructor(Class.forName(parent)).newInstance(clzz.newInstance()))
                                        .minWorkerThreads((Integer.parseInt(entry.getValue().get(".min_threads"))))
                                        .maxWorkerThreads(Integer.parseInt(entry.getValue().get(".max_threads")))
                        )
                )
                ;
                logger.info("start [" + impClassName + "] server on port " + entry.getValue().get(".port"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void start() {
        for (final TServer tServer : servers) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    tServer.serve();
                }
            }).start();

        }
    }

    public void stop() {
        for (TServer tServer : servers) {
            tServer.stop();
        }
    }

}
