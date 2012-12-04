package com.example.controller;


import net.csdn.annotation.filter.AroundFilter;
import net.csdn.modules.http.WowAroundFilter;

import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-7-29
 * Time: 下午3:48
 */
public abstract class ApplicationController extends net.csdn.modules.http.ApplicationController {

    public static String OK = "{\"ok\":true,\"message\":\"{}\"}";
    public static String FAIL = "{\"ok\":false,\"message\":\"{}\"}";


    public static String ok(String msg) {
        return format(OK, msg);
    }

    public static String fail(String msg) {
        return format(FAIL, msg);
    }

    public static String ok() {
        return format(OK, "");
    }

    public static String fail() {
        return format(FAIL, "");
    }

    @AroundFilter
    private final static Map $print_action_execute_time = map();

    private void print_action_execute_time(WowAroundFilter wowAroundFilter)throws Exception {
        long time1 = System.currentTimeMillis();
        wowAroundFilter.invoke();
        logger.info("execute time:[" + (System.currentTimeMillis() - time1) + "]");
    }

}
