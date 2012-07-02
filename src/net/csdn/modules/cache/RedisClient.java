package net.csdn.modules.cache;

/**
 * User: WilliamZhu
 * Date: 12-6-28
 * Time: 上午7:33
 */

import com.google.inject.Inject;
import net.csdn.common.settings.Settings;
import net.csdn.exception.SettingsException;
import net.csdn.modules.compress.gzip.GZip;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class RedisClient {

    private JedisPool pool;

    @Inject
    public RedisClient(Settings settings) {
        try {
            pool = new JedisPool(new JedisPoolConfig(), settings.get("redis.host"), settings.getAsInt("redis.port", 6379));
        } catch (SettingsException e) {
            // ignore
        }
    }


    public interface Callback {
        public void execute(Jedis jedis);
    }

    public void operate(Callback callback) {
        Jedis jedis = borrow();
        try {
            callback.execute(jedis);
        } finally {
            revert(jedis);
        }
    }

    public String get(String key) {
        Jedis jedis = borrow();
        try {
            return jedis.get(key);
        } finally {
            revert(jedis);
        }
    }

    public String bGet(String key) {
        Jedis jedis = borrow();
        try {
            byte[] value = jedis.get(key.getBytes());
            if (value != null) return GZip.decodeWithGZip(value);
            return null;
        } finally {
            revert(jedis);
        }
    }

    public String set(String key, String value) {
        Jedis jedis = borrow();
        try {
            return jedis.set(key, value);
        } finally {
            revert(jedis);
        }
    }

    public void del(String key) {
        Jedis jedis = borrow();
        try {
            jedis.del(key);
        } finally {
            revert(jedis);
        }
    }

    public String bSet(String key, String value) {
        Jedis jedis = borrow();
        try {
            return jedis.set(key.getBytes(), GZip.encodeWithGZip(value));
        } finally {
            revert(jedis);
        }
    }

    public boolean exits(String key) {
        Jedis jedis = borrow();
        try {
            return jedis.exists(key);
        } finally {
            revert(jedis);
        }
    }

    public List<String> mget(String[] keys) {
        Jedis jedis = borrow();
        try {
            return jedis.mget(keys);
        } finally {
            revert(jedis);
        }
    }

    public String info() {
        Jedis jedis = borrow();
        try {
            return jedis.info();
        } finally {
            revert(jedis);
        }
    }

    public List<String> bMget(String[] keys) {
        Jedis jedis = borrow();
        int len = keys.length;
        byte[][] bkeys = new byte[len][];
        for (int i = 0; i < keys.length; i++) {
            bkeys[i] = keys[i].getBytes();
        }
        try {
            List<byte[]> list = jedis.mget(bkeys);
            List<String> temp_list = new ArrayList<String>(list.size());
            for (byte[] temp : list) {
                temp_list.add(GZip.decodeWithGZip(temp));
            }
            return temp_list;
        } finally {
            revert(jedis);
        }
    }

    public Set<String> sCopy(String key, String new_key) {
        Jedis jedis = borrow();
        try {
            Set<String> oldSets = jedis.smembers(key);
            for (String str : oldSets) {
                jedis.sadd(new_key, str);
            }
            return oldSets;
        } finally {
            revert(jedis);
        }
    }

    public void sClear(String key, String oldKey) {
        Jedis jedis = borrow();
        try {
            Set<String> oldSets = jedis.smembers(key);
            for (String str : oldSets) {
                jedis.del(oldKey + ":" + str);
            }
            jedis.del(key);
        } finally {
            revert(jedis);
        }
    }

    public Set<String> sMove(String key, String new_key) {
        Jedis jedis = borrow();
        try {
            Set<String> oldSets = jedis.smembers(key);
            for (String str : oldSets) {
                jedis.smove(key, new_key, str);
            }
            return oldSets;
        } finally {
            revert(jedis);
        }
    }


    public void destory() {
        pool.destroy();
    }

    public Jedis borrow() {
        return pool.getResource();
    }

    public void revert(Jedis jedis) {
        pool.returnResource(jedis);
    }
}

