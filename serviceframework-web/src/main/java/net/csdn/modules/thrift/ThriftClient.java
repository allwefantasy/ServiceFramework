package net.csdn.modules.thrift;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.modules.thrift.pool.*;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 6/3/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ThriftClient<T extends TServiceClient> {

    private CSLogger logger = Loggers.getLogger(ThriftClient.class);

    private final int connectTimeoutInMillis = 1000;
    private Class<T> hold;

    private final static Map<Class, ThriftClient> pools = new ConcurrentHashMap<Class, ThriftClient>();

    public static synchronized ThriftClient build(Class type) {
        if (pools.containsKey(type)) {
            return pools.get(type);
        }
        ThriftClient thriftClient = new ThriftClient();
        thriftClient.hold = type;
        pools.put(type, thriftClient);
        return thriftClient;
    }

    private ThriftClient() {
    }

    final ObjectPool<String, T> connectionPool = new BaseObjectPool.Builder<String, T>(
            new PoolableObjectFactory<String, T>() {
                @Override
                public T createObject(String key) throws Exception {
                    String[] socketInfo = key.split(":");
                    TTransport transport = new TSocket(socketInfo[0], Integer.parseInt(socketInfo[1]));
                    transport.open();
                    TProtocol protocol = new TCompactProtocol(transport);
                    return hold.getConstructor(TProtocol.class).newInstance(protocol);
                }

                @Override
                public void destroyObject(String key, T value) throws Exception {
                    if (value != null) {
                        TProtocol inputTProtocol = value.getInputProtocol();
                        closeTransport(inputTProtocol);
                        TProtocol outputTProtocol = value.getOutputProtocol();
                        closeTransport(outputTProtocol);
                    }

                }

                private void closeTransport(TProtocol protocol) {
                    if (protocol == null) return;
                    TTransport transport = protocol.getTransport();
                    try {
                        if (transport != null && transport.isOpen()) {
                            transport.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public boolean validateObject(String key, T value) throws Exception {
                    //if (value == null) return false;
                    //ReflectHelper.method(value,"");
                    return true;
                }
            }
    ).borrowValidation(true).min(10).max(50).build();


    public void execute(String address, Callback<T> callback) throws ThriftConnectException {
        T obj = brow(address);
        try {
            callback.execute(obj);
        } finally {
            back(address, obj);
        }
    }


    public void tryExecute(String address, Callback<T> callback,long tryTime,int tryCount) throws ThriftConnectException {
        T obj = null;
        int failCount = 0;
        boolean shouldTry = true;

        while (shouldTry && failCount < tryCount) {
            try {
                obj = brow(address);
                shouldTry = false;
            } catch (ThriftConnectException e) {
                e.printStackTrace();
                try {
                    Thread.sleep(tryTime);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                failCount++;
                if (failCount == 4) {
                    throw e;
                }
            }
        }

        try {
            callback.execute(obj);
        } finally {
            back(address, obj);
        }
    }

    public T brow(String socketAddress) throws ThriftConnectException {
        try {
            return connectionPool.borrowObject(socketAddress, 1000);
        } catch (PoolExhaustedException e) {
            logger.error("thrift client 连接池耗尽");
            e.printStackTrace();
            throw new ThriftConnectException(e);
        } catch (NoValidObjectException e) {
            logger.error("thrift Connection对象出现问题");
            e.printStackTrace();
            throw new ThriftConnectException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void back(String socketAddress, T value) {
        try {
            connectionPool.returnObject(socketAddress, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface Callback<T> {
        public void execute(T t);

    }

}
