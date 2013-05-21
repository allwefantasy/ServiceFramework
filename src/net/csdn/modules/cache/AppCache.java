package net.csdn.modules.cache;

import com.google.inject.Inject;
import net.csdn.common.exception.SettingsException;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.Callable;

/**
 * 4/13/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class AppCache<T> implements Cache {

    private JedisPool pool;
    private CSLogger logger = Loggers.getLogger(AppCache.class);

    @Inject
    public AppCache(Settings settings) {
        try {
            pool = new JedisPool(new JedisPoolConfig(), settings.get("redis.host"), settings.getAsInt("redis.port", 6379));
        } catch (SettingsException e) {
            // ignore
        }
    }

    public String fetch(String key) {
        Jedis client = null;
        try {
            client = pool.getResource();
            return client.get(key);
        } finally {
            if (client != null) {
                if (!client.isConnected()) pool.returnBrokenResource(client);
                else pool.returnResource(client);
            }

        }
    }


    public long increment(String key) {
        Jedis client = null;
        try {
            client = pool.getResource();
            return client.incr(key);
        } finally {
            if (client != null) {
                if (!client.isConnected()) pool.returnBrokenResource(client);
                else pool.returnResource(client);
            }

        }
    }

    public String read(String key) {
        return fetch(key);
    }

    public Long remove(String key) {
        return delete(key);

    }

    public Long delete(String key) {
        Jedis client = null;
        try {
            client = pool.getResource();
            return client.del(key);
        } finally {
            if (client != null) {
                if (!client.isConnected()) pool.returnBrokenResource(client);
                else pool.returnResource(client);
            }

        }
    }

    public String fetch(String key, Callable callable) {


        Jedis client = null;
        try {
            client = pool.getResource();
            String temp = client.get(key);
            if (temp == null) {
                client.set(key, callable.call().toString());
                return (String) callable.call();
            } else {
                return temp;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (client != null) {
                if (!client.isConnected()) pool.returnBrokenResource(client);
                else pool.returnResource(client);
            }

        }


    }

}
