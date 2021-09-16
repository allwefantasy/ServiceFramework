package net.csdn.modules.controller;

import tech.mlsql.common.utils.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;

import net.sf.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class QpsManager {

    private String module = "qps";

    private Map<String, Integer> qpsConfs = new ConcurrentHashMap<String, Integer>();

    //统计周期
    private long timePeriod;

    //当前qps和前一个周期qps
    private Map<String, QpsValue> currentQps = new ConcurrentHashMap<String, QpsValue>();


    private CSLogger logger = Loggers.getLogger(QpsManager.class);
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, Executors.defaultThreadFactory());
    private Settings settings;

    public String qpsConfs() {
        return JSONObject.fromObject(qpsConfs).toString();
    }

    public void configureQpsLimiter(Map<String, String> config) {
        boolean qpsLimitEnable = settings.getAsBoolean("qpslimit.enable", false);
        if (qpsLimitEnable) {
            for (Map.Entry<String, String> conf : config.entrySet()) {
                qpsConfs.put(conf.getKey(), Integer.parseInt(conf.getValue()));
            }
        }
    }

    @Inject
    public QpsManager(Settings settings) {
        this.settings = settings;
        ImmutableMap<String, String> confs = settings.getByPrefix("qps.").getAsMap();
        if (confs != null) {

            logger.info("加载配置 " + confs);
            for (Map.Entry<String, String> conf : confs.entrySet()) {
                qpsConfs.put(conf.getKey(), Integer.parseInt(conf.getValue()));
            }
        }

        timePeriod = settings.getAsLong("qps.period", 3000l);

        logger.info("统计周期 " + timePeriod + "秒");
        scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                logger.debug("开始qps数据切换");
                currentQps = new ConcurrentHashMap<String, QpsValue>();
            }
        }, 1000l, timePeriod, TimeUnit.MILLISECONDS);


    }

    /**
     * 是否已经超过指定的qps
     */
    public boolean check(String type) {

        Integer max = qpsConfs.get(type);
        if (max == null) {
            return false;
        }
        //保存数据

        QpsValue currentQpsValue = currentQps.get(type);
        if (currentQpsValue == null) {
            synchronized (currentQps) {
                currentQpsValue = currentQps.get(type);
                if (currentQpsValue == null) {
                    currentQpsValue = new QpsValue();
                    currentQps.put(type, currentQpsValue);
                }
            }
        }

        currentQpsValue.value.incrementAndGet();

        if (max != null && currentQpsValue != null && max.intValue() < (currentQpsValue.value.get() * 1000 / timePeriod)) {
            return true;
        } else {
            return false;
        }


    }


    public static class QpsValue {
        public AtomicInteger value = new AtomicInteger();
    }
}