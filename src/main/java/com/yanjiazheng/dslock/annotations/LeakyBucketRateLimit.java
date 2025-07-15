package com.yanjiazheng.dslock.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author hp
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LeakyBucketRateLimit {
    int capacity() default 100;

    int rate(); // 每秒处理能力
}
