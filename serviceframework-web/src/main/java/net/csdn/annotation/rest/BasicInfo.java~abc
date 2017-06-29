package net.csdn.annotation.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: WilliamZhu
 * Date: 12-6-7
 * Time: 下午4:15
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BasicInfo {

    //描述
    String desc();

    //url类型参数
    String testParams();

    //支持正则
    String testResult();

    //参考State
    State state();

    String author();

    String email();
}

