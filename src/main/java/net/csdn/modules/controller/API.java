package net.csdn.modules.controller;

import com.google.inject.Inject;
import net.csdn.annotation.FDesc;
import net.csdn.annotation.MDesc;
import net.csdn.annotation.Param;
import net.csdn.annotation.rest.At;
import net.csdn.annotation.rest.BasicInfo;
import net.csdn.common.collect.Tuple3;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import org.joda.time.DateTime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 4/9/14 WilliamZhu(allwefantasy@gmail.com)
 */

public class API {
    private Settings settings;

    private CSLogger logger = Loggers.getLogger(API.class);
    /*
      在特定internal 时间内的QPS
     */
    private ConcurrentHashMap<Method, Tuple3<AtomicLong/*时间戳*/, AtomicLong/*当前interval时间内正在累积的访问数*/, AtomicLong/*上一个interval计算出的结果QPS*/>> APIQPS = new ConcurrentHashMap<Method, Tuple3<AtomicLong, AtomicLong, AtomicLong>>();

    /*
      每个API 从系统启动开始，各个http状态码数目
     */
    private ConcurrentHashMap<Method, ConcurrentHashMap<Integer, AtomicLong>> APISTATUS = new ConcurrentHashMap<Method, ConcurrentHashMap<Integer, AtomicLong>>();

    private ConcurrentHashMap<Method, APIDesc> APIDescs = new ConcurrentHashMap<Method, APIDesc>();

    private Long SystemStartTime = 0l;
    private boolean forceAPICheck = false;


    private int internal = 1000;

    @Inject
    public API(Settings settings) {
        this.settings = settings;
        this.internal = settings.getAsInt("application.api.qps.internal", 1000);
        this.forceAPICheck = settings.getAsBoolean("application.api.strict.check", false);
        this.SystemStartTime = System.currentTimeMillis();
    }

    /*
     controller初始化时需要调用该方法
     */
    public void addPath(Method api) {
        APIQPS.putIfAbsent(api, new Tuple3<AtomicLong, AtomicLong, AtomicLong>(new AtomicLong(SystemStartTime), new AtomicLong(), new AtomicLong()));
        APISTATUS.putIfAbsent(api, new ConcurrentHashMap<Integer, AtomicLong>());
    }

    /*
      校验API是否填写各种说明
     */
    public boolean validateAPI() {
        if (forceAPICheck) {
            return true;
        }
        return true;
    }


    /*
     收集每个API的详细信息
     */
    public void collectAPIInfoes() {

        for (Method method : APIQPS.keySet()) {
            At path = method.getAnnotation(At.class);
            MDesc mDesc = method.getAnnotation(MDesc.class);
            BasicInfo basicInfo = method.getAnnotation(BasicInfo.class);

            APIDesc apiDesc = new APIDesc();
            apiDesc.path = path.path()[0];
            apiDesc.desc = mDesc != null ? mDesc.value() : "";
            apiDesc.qps = APIQPS.get(method).v3().get();
            apiDesc.paramDesces = createParamDescs(method);
            List<ResponseStatus> responseStatuses = new ArrayList<ResponseStatus>();
            for (Map.Entry<Integer, AtomicLong> item : APISTATUS.get(method).entrySet()) {
                ResponseStatus responseStatus = new ResponseStatus();
                responseStatus.status = item.getKey();
                responseStatus.count = item.getValue().get();
                responseStatuses.add(responseStatus);
            }
            apiDesc.responseStatuses = responseStatuses;
            APIDescs.put(method,apiDesc);
        }

    }

    private List<ParamDesc> createParamDescs(Method method) {
        List<ParamDesc> paramDescs = new ArrayList<ParamDesc>();
        Annotation[][] paramAnnoes = method.getParameterAnnotations();
        Class[] types = method.getParameterTypes();
        for (int i = 0; i < paramAnnoes.length; i++) {
            Annotation[] paramAnno = paramAnnoes[i];
            if (paramAnno.length == 0) continue;
            ParamDesc paramDesc = new ParamDesc();
            for (Annotation item : paramAnno) {
                if (item instanceof FDesc) {
                    paramDesc.desc = ((FDesc) item).value();
                }
                if (item instanceof Param) {
                    paramDesc.name = ((Param) item).value();
                }
            }
            paramDesc.ptype = types[i].getName();
            paramDescs.add(paramDesc);
        }
        return paramDescs;
    }

    class APIDesc {
        String path;
        String desc;
        long qps;
        List<ParamDesc> paramDesces;
        List<ResponseStatus> responseStatuses;
    }

    class ParamDesc {
        String name;
        String desc;
        String ptype;
    }

    class ResponseStatus {
        int status;
        long count;
    }

    /*
      QPS 统计
     */
    public synchronized void qpsIncrement(Method api) {
        if(api==null)return;
        long now = System.currentTimeMillis();
        Tuple3<AtomicLong, AtomicLong, AtomicLong> info = APIQPS.get(api);
        if (now - info.v1().get() > internal) {
            info.v3().set(info.v2().get());
            info.v2().set(0);
            info.v1().set(now);
        } else {
            info.v2().incrementAndGet();
        }

    }

    /*
      状态码统计
     */
    public synchronized void statusIncrement(Method api, int status) {
        if(api==null)return;
        ConcurrentHashMap<Integer, AtomicLong> chm = APISTATUS.get(api);
        if (!chm.contains(status)) {
            chm.put(status, new AtomicLong());
        }
        chm.get(status).incrementAndGet();
    }

    public String systemStartTime() {
        return new DateTime(SystemStartTime).toString("yyyy-MM-dd HH-mm-ss S");
    }

}
