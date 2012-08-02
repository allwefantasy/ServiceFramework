package com.example.controller;


import net.csdn.annotation.filter.AroundFilter;
import net.csdn.modules.http.RestController;

import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-7-29
 * Time: 下午3:48
 */
public class ApplicationController extends net.csdn.modules.http.ApplicationController {

    @AroundFilter
    private final static Map $print_action_execute_time = map();

    private void print_action_execute_time(RestController.WowAroundFilter wowAroundFilter) {
        long time1 = System.currentTimeMillis();
        wowAroundFilter.invoke();
        logger.info("execute time:[" + (System.currentTimeMillis() - time1) + "]");
    }

}
